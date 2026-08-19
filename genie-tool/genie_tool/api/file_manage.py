import mimetypes
import os
from pathlib import Path
from urllib.parse import quote, unquote

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse, JSONResponse, Response
from starlette.requests import Request

from genie_tool.db.file_table_op import (
    FileInfoOp,
    get_file_download_url,
    get_file_preview_url,
)
from genie_tool.model.protocal import (
    FileListRequest,
    FileRequest,
    FileUploadRequest,
    get_file_id,
)
from genie_tool.util.file_policy import (
    MAX_FILE_BYTES,
    FilePolicyError,
    normalize_file_name,
    normalize_request_id,
)
from genie_tool.util.internal_auth import require_internal_file_token
from genie_tool.util.middleware_util import RequestHandlerRoute


_MAX_MULTIPART_OVERHEAD_BYTES = 1024 * 1024


class FileManageRoute(RequestHandlerRoute):
    """Reject oversized multipart bodies before FastAPI spools them to disk."""

    def get_route_handler(self):
        handler = super().get_route_handler()

        async def limited_handler(request: Request):
            if request.method == "POST" and request.url.path.endswith("/upload_file_data"):
                raw_length = request.headers.get("content-length")
                if raw_length:
                    try:
                        content_length = int(raw_length)
                    except ValueError as error:
                        raise HTTPException(
                            status_code=400, detail="invalid content length"
                        ) from error
                    if content_length > MAX_FILE_BYTES + _MAX_MULTIPART_OVERHEAD_BYTES:
                        raise HTTPException(
                            status_code=413, detail="file exceeds the size limit"
                        )
            return await handler(request)

        return limited_handler


router = APIRouter(route_class=FileManageRoute)


def _policy_http_error(error: FilePolicyError) -> HTTPException:
    status = 413 if error.code == "FILE_TOO_LARGE" else 400
    return HTTPException(status_code=status, detail=str(error))


def _scope(request_id: str) -> str:
    try:
        return normalize_request_id(request_id)
    except FilePolicyError as error:
        raise _policy_http_error(error) from error


def _scope_and_name(request_id: str, file_name: str) -> tuple[str, str]:
    try:
        return _scope(request_id), normalize_file_name(file_name)
    except FilePolicyError as error:
        raise _policy_http_error(error) from error


def _response(file_info, request_id: str):
    preview_url = get_file_preview_url(file_id=request_id, file_name=file_info.filename)
    download_url = get_file_download_url(file_id=request_id, file_name=file_info.filename)
    return {
        "fileId": file_info.file_id,
        "ossUrl": download_url,
        "downloadUrl": download_url,
        "domainUrl": preview_url,
        "requestId": request_id,
        "fileName": file_info.filename,
        "fileSize": file_info.file_size,
    }


def _stored_file_path(file_info) -> Path | None:
    try:
        root = Path(os.getenv("FILE_SAVE_PATH", "file_db_dir")).resolve()
        candidate = Path(file_info.file_path).resolve()
        if root == candidate or root not in candidate.parents:
            return None
        return candidate
    except (OSError, RuntimeError, TypeError):
        return None


@router.post("/get_file")
async def get_file(body: FileRequest, _: None = Depends(require_internal_file_token)):
    request_id, file_name = _scope_and_name(body.request_id, body.file_name)
    file_info = await FileInfoOp.get_by_request_id_and_filename(request_id, file_name)
    if not file_info or file_info.status != 1 or file_info.request_id != request_id:
        raise HTTPException(status_code=404, detail="file not found")
    return JSONResponse(content=_response(file_info, request_id))


@router.post("/upload_file")
async def upload_file(body: FileUploadRequest, _: None = Depends(require_internal_file_token)):
    request_id, file_name = _scope_and_name(body.request_id, body.file_name)
    try:
        file_info = await FileInfoOp.add_by_content(
            filename=file_name,
            content=body.content,
            file_id=get_file_id(request_id, file_name),
            description=body.description,
            request_id=request_id,
        )
    except FilePolicyError as error:
        raise _policy_http_error(error) from error
    return JSONResponse(content=_response(file_info, request_id))


@router.post("/upload_file_data")
async def upload_file_data(
    file: UploadFile = File(...),
    request_id: str = Form(alias="requestId"),
    _: None = Depends(require_internal_file_token),
):
    if file.size is not None and file.size > MAX_FILE_BYTES:
        raise HTTPException(status_code=413, detail="file exceeds the size limit")
    request_id, file_name = _scope_and_name(request_id, unquote(file.filename or ""))
    file.filename = file_name
    try:
        file_info = await FileInfoOp.add_by_file(
            file=file,
            file_id=get_file_id(request_id, file_name),
            request_id=request_id,
        )
    except FilePolicyError as error:
        raise _policy_http_error(error) from error
    return JSONResponse(content=_response(file_info, request_id))


@router.post("/get_file_list")
async def get_file_list(body: FileListRequest, _: None = Depends(require_internal_file_token)):
    request_id = _scope(body.request_id)
    if not body.filters:
        file_infos = await FileInfoOp.get_by_request_id(request_id)
    else:
        file_names = set()
        for item in body.filters:
            if item.request_id != request_id:
                raise HTTPException(status_code=400, detail="filter scope mismatch")
            file_names.add(_scope_and_name(request_id, item.file_name)[1])
        file_infos = [
            item
            for item in await FileInfoOp.get_by_request_id(request_id)
            if item.filename in file_names
        ]
    file_infos = [
        item for item in file_infos if item.status == 1 and item.request_id == request_id
    ]
    page_size = min(max(body.page_size, 1), 200)
    start = max(body.page - 1, 0) * page_size
    page_items = file_infos[start : start + page_size]
    total_size = sum(item.file_size or 0 for item in file_infos)
    results = [_response(item, request_id) for item in page_items]
    return JSONResponse(content={"results": results, "totalSize": total_size})


def _usable_stored_file(file_info, expected_name: str, expected_scope: str | None = None):
    if (
        not file_info
        or file_info.status != 1
        or file_info.filename != expected_name
        or (expected_scope is not None and file_info.request_id != expected_scope)
    ):
        return None
    path = _stored_file_path(file_info)
    if path is None or not path.is_file():
        return None
    return file_info


async def _get_route_file(file_id: str, file_name: str):
    """Resolve a public download/preview URL.

    New workspace URLs put the stored file_id in the first segment. Product
    FileTool URLs still put requestId there. Historical rows may also use the
    pre-sha256 md5 file_id, so lookup tries the stored id first, then the
    requestId + filename pair, then both hash algorithms.
    """
    raw_file_id = unquote(file_id)
    try:
        file_name = normalize_file_name(unquote(file_name))
    except FilePolicyError as error:
        raise _policy_http_error(error) from error

    by_id = _usable_stored_file(await FileInfoOp.get_by_file_id(raw_file_id), file_name)
    if by_id is not None:
        return by_id, by_id.request_id, file_name

    try:
        request_id = normalize_request_id(raw_file_id)
    except FilePolicyError:
        return None, raw_file_id, file_name

    by_scope = _usable_stored_file(
        await FileInfoOp.get_by_request_id_and_filename(request_id, file_name),
        file_name,
        request_id,
    )
    if by_scope is not None:
        return by_scope, request_id, file_name

    by_computed = _usable_stored_file(
        await FileInfoOp.get_by_computed_file_id(request_id, file_name),
        file_name,
        request_id,
    )
    if by_computed is not None:
        return by_computed, request_id, file_name
    return None, request_id, file_name


@router.get("/download/{file_id}/{file_name}")
async def download_file(file_id: str, file_name: str):
    file_info, request_id, file_name = await _get_route_file(file_id, file_name)
    if file_info is None:
        return Response(content="File not found", status_code=404)
    path = _stored_file_path(file_info)
    return FileResponse(
        path,
        filename=os.path.basename(file_info.filename),
        headers={
            "Content-Disposition": f'attachment; filename="{quote(file_name)}"; filename*=UTF-8\'\'{quote(file_name)}',
        },
    )


@router.get("/preview/{file_id}/{file_name}")
async def preview_file(file_id: str, file_name: str):
    file_info, request_id, file_name = await _get_route_file(file_id, file_name)
    if file_info is None:
        return Response(content="File not found", status_code=404)

    content_type, _ = mimetypes.guess_type(file_name)
    disposition = "inline" if content_type else "attachment"
    return FileResponse(
        _stored_file_path(file_info),
        filename=os.path.basename(file_info.filename),
        media_type=content_type or "application/octet-stream",
        headers={
            "Content-Disposition": f'{disposition}; filename="{quote(file_name)}"; filename*=UTF-8\'\'{quote(file_name)}',
        },
    )
