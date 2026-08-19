# -*- coding: utf-8 -*-
"""Workspace file API — scoped to a server-computed requestId.

This module exposes file list / upload / download / preview under
  /v1/workspaces/{conversationId}/files
and is only callable with an internal file token from the Java backend.
"""

from __future__ import annotations

import mimetypes
import os
from pathlib import Path
from urllib.parse import quote, unquote

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, UploadFile
from fastapi.responses import FileResponse, JSONResponse, Response
from starlette.requests import Request

from genie_tool.db.file_table_op import (
    FileInfoOp,
    get_file_download_url,
    get_file_preview_url,
)
from genie_tool.model.protocal import get_file_id
from genie_tool.util.file_policy import (
    MAX_FILE_BYTES,
    FilePolicyError,
    normalize_file_name,
    normalize_request_id,
)
from genie_tool.util.internal_auth import require_internal_file_token
from genie_tool.util.middleware_util import RequestHandlerRoute


_MAX_MULTIPART_OVERHEAD_BYTES = 1024 * 1024


class WorkspaceFileRoute(RequestHandlerRoute):
    """Reject oversized multipart bodies before FastAPI spools them to disk."""

    def get_route_handler(self):
        handler = super().get_route_handler()

        async def limited_handler(request: Request):
            if request.method == "POST" and request.url.path.endswith("/files"):
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


router = APIRouter(
    route_class=WorkspaceFileRoute,
    dependencies=[Depends(require_internal_file_token)],
)


def _policy_http_error(error: FilePolicyError) -> HTTPException:
    status = 413 if error.code == "FILE_TOO_LARGE" else 400
    return HTTPException(status_code=status, detail=str(error))


def _normalize_conversation_id(value: str) -> str:
    try:
        return normalize_request_id(value)
    except FilePolicyError as error:
        raise _policy_http_error(error) from error


def _build_request_id(conversation_id: str, request_id: str | None = None) -> str:
    """Use the already-authenticated scope from the internal caller."""
    _normalize_conversation_id(conversation_id)
    if not request_id:
        raise HTTPException(status_code=400, detail="requestId is required")
    try:
        return normalize_request_id(request_id)
    except FilePolicyError as error:
        raise _policy_http_error(error) from error


def _file_response(file_info, request_id: str):
    preview_url = get_file_preview_url(file_id=file_info.file_id, file_name=file_info.filename)
    download_url = get_file_download_url(file_id=file_info.file_id, file_name=file_info.filename)
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


@router.get("/{conversation_id}/files")
async def list_workspace_files(
    conversation_id: str,
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=200, ge=1, le=200),
    request_id: str | None = Query(default=None, alias="requestId"),
):
    """List files scoped to a workspace conversationId."""
    try:
        conversation_id = _normalize_conversation_id(conversation_id)
    except FilePolicyError as error:
        raise _policy_http_error(error) from error

    request_id = _build_request_id(conversation_id, request_id)

    try:
        file_infos = await FileInfoOp.get_by_request_id(request_id)
    except Exception as error:
        raise HTTPException(status_code=500, detail="Failed to list files") from error

    file_infos = [
        item for item in file_infos if item.status == 1 and item.request_id == request_id
    ]
    start = max(page - 1, 0) * page_size
    page_items = file_infos[start: start + page_size]
    total_size = sum(item.file_size or 0 for item in file_infos)
    results = [_file_response(item, request_id) for item in page_items]
    return JSONResponse(content={"results": results, "totalSize": total_size})


@router.post("/{conversation_id}/files")
async def upload_workspace_file(
    conversation_id: str,
    file: UploadFile = File(...),
    request_id: str = Form(alias="requestId", default=""),
):
    """Upload a file scoped to a workspace conversationId."""
    try:
        conversation_id = _normalize_conversation_id(conversation_id)
    except FilePolicyError as error:
        raise _policy_http_error(error) from error

    if file.size is not None and file.size > MAX_FILE_BYTES:
        raise HTTPException(status_code=413, detail="file exceeds the size limit")

    request_id = _build_request_id(conversation_id, request_id or None)

    try:
        file_name = normalize_file_name(unquote(file.filename or ""))
    except FilePolicyError as error:
        raise _policy_http_error(error) from error

    file.filename = file_name
    file_id = get_file_id(request_id, file_name)

    try:
        result = await FileInfoOp.add_by_file(
            file=file,
            file_id=file_id,
            request_id=request_id,
        )
    except FilePolicyError as error:
        raise _policy_http_error(error) from error

    return JSONResponse(content={
        "fileId": result["file_id"],
        "ossUrl": get_file_download_url(file_id=result["file_id"], file_name=result["filename"]),
        "downloadUrl": get_file_download_url(file_id=result["file_id"], file_name=result["filename"]),
        "domainUrl": get_file_preview_url(file_id=result["file_id"], file_name=result["filename"]),
        "requestId": request_id,
        "fileName": result["filename"],
        "fileSize": result["file_size"],
    })


@router.get("/{conversation_id}/files/{file_name:path}/download")
async def download_workspace_file(
    conversation_id: str,
    file_name: str,
    request_id: str | None = Query(default=None, alias="requestId"),
):
    """Download a file from workspace scope."""
    try:
        conversation_id = _normalize_conversation_id(conversation_id)
        file_name = normalize_file_name(unquote(file_name)) if file_name else ""
    except FilePolicyError as error:
        raise _policy_http_error(error) from error

    request_id = _build_request_id(conversation_id, request_id)

    file_info = await FileInfoOp.get_by_request_id_and_filename(request_id, file_name)
    path = _stored_file_path(file_info) if file_info else None

    if (
        not file_info
        or file_info.status != 1
        or file_info.request_id != request_id
        or file_info.filename != file_name
        or path is None
        or not path.is_file()
    ):
        return Response(content="File not found", status_code=404)

    return FileResponse(
        path,
        filename=os.path.basename(file_info.filename),
        headers={
            "Content-Disposition": f'attachment; filename="{quote(file_name)}"; filename*=UTF-8\'\'{quote(file_name)}',
        },
    )


@router.get("/{conversation_id}/files/{file_name:path}/preview")
async def preview_workspace_file(
    conversation_id: str,
    file_name: str,
    request_id: str | None = Query(default=None, alias="requestId"),
):
    """Preview a file from workspace scope."""
    try:
        conversation_id = _normalize_conversation_id(conversation_id)
        file_name = normalize_file_name(unquote(file_name)) if file_name else ""
    except FilePolicyError as error:
        raise _policy_http_error(error) from error

    request_id = _build_request_id(conversation_id, request_id)

    file_info = await FileInfoOp.get_by_request_id_and_filename(request_id, file_name)
    path = _stored_file_path(file_info) if file_info else None

    if (
        not file_info
        or file_info.status != 1
        or file_info.request_id != request_id
        or file_info.filename != file_name
        or path is None
        or not path.is_file()
    ):
        return Response(content="File not found", status_code=404)

    content_type, _ = mimetypes.guess_type(file_name)
    disposition = "inline" if content_type else "attachment"
    return FileResponse(
        path,
        filename=os.path.basename(file_info.filename),
        media_type=content_type or "application/octet-stream",
        headers={
            "Content-Disposition": f'{disposition}; filename="{quote(file_name)}"; filename*=UTF-8\'\'{quote(file_name)}',
        },
    )
