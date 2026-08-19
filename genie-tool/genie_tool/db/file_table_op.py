import os
import tempfile
from pathlib import Path
from typing import List
from urllib.parse import quote

from fastapi import UploadFile
from sqlmodel import select

from genie_tool.db.file_table import FileInfo
from genie_tool.db.db_engine import async_session_local
from genie_tool.util.file_policy import (
    FilePolicyError,
    assert_text_size,
    copy_limited,
    normalize_file_name,
    normalize_request_id,
    safe_storage_path,
)
from genie_tool.util.log_util import timer


class _FileDB(object):
    def __init__(self):
        self._work_dir = Path(os.getenv("FILE_SAVE_PATH", "file_db_dir")).resolve()
        self._work_dir.mkdir(parents=True, exist_ok=True)

    async def save(self, file_name, content, scope, file_id) -> str:
        normalized_name = normalize_file_name(file_name)
        normalized_scope = normalize_request_id(scope)
        assert_text_size(content)
        save_path = safe_storage_path(self._work_dir, normalized_scope, file_id)
        save_path.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=save_path.parent,
            delete=False,
        ) as temporary:
            temporary.write(content)
            temporary_path = Path(temporary.name)
        temporary_path.replace(save_path)
        return str(save_path)

    async def save_by_data(self, file: UploadFile, scope, file_id) -> str:
        normalized_scope = normalize_request_id(scope)
        normalize_file_name(file.filename)
        save_path = safe_storage_path(self._work_dir, normalized_scope, file_id)
        save_path.parent.mkdir(parents=True, exist_ok=True)
        temporary_path = None
        try:
            with tempfile.NamedTemporaryFile(
                mode="wb", dir=save_path.parent, delete=False
            ) as destination:
                temporary_path = Path(destination.name)
                await copy_limited(file.read, destination)
            temporary_path.replace(save_path)
        except BaseException:
            if temporary_path:
                temporary_path.unlink(missing_ok=True)
            raise
        return str(save_path)


FileDB = _FileDB()


class FileInfoOp(object):

    @classmethod
    @timer()
    async def add_by_content(cls, filename: str, content: str, file_id: str, description: str = None,
                             request_id: str = None) -> FileInfo:
        normalized_name = normalize_file_name(filename)
        normalized_scope = normalize_request_id(request_id)
        file_path = await FileDB.save(
            normalized_name, content, normalized_scope, file_id
        )
        file_info = FileInfo(
            file_id=file_id,
            filename=normalized_name,
            file_path=file_path,
            description=description,
            file_size=os.path.getsize(file_path),
            status=1,
            request_id=normalized_scope
        )
        try:
            return await cls.add(file_info)
        except BaseException:
            Path(file_path).unlink(missing_ok=True)
            raise

    @staticmethod
    @timer()
    async def add_by_file(file: UploadFile, file_id: str, request_id: str = None) -> dict:
        normalized_name = normalize_file_name(file.filename)
        normalized_scope = normalize_request_id(request_id)
        file_path = await FileDB.save_by_data(file, normalized_scope, file_id)
        file_info = FileInfo(
            file_id=file_id,
            filename=normalized_name,
            file_path=file_path,
            description="",
            file_size=os.path.getsize(file_path),
            status=1,
            request_id=normalized_scope
        )
        try:
            saved_info = await FileInfoOp.add(file_info)
            return {
                "file_id": saved_info.file_id,
                "filename": saved_info.filename,
                "file_size": saved_info.file_size,
            }
        except BaseException:
            Path(file_path).unlink(missing_ok=True)
            raise

    @staticmethod
    @timer()
    async def add(file_info: FileInfo) -> FileInfo:
        old_path = None
        stored_filename = file_info.filename
        stored_request_id = file_info.request_id
        async with async_session_local() as session:
            existing = await session.run_sync(
                lambda s: s.query(FileInfo)
                .filter(
                    FileInfo.request_id == file_info.request_id,
                    FileInfo.filename == file_info.filename
                )
                .first()
            )
            if existing and existing.file_id != file_info.file_id:
                file_info.file_id = existing.file_id
                replacement_path = safe_storage_path(
                    FileDB._work_dir,
                    file_info.request_id,
                    file_info.file_id,
                )
                source_path = Path(file_info.file_path)
                replacement_path.parent.mkdir(parents=True, exist_ok=True)
                if source_path != replacement_path:
                    source_path.replace(replacement_path)
                file_info.file_path = str(replacement_path)
            if not existing:
                collision = await session.run_sync(
                    lambda s: s.query(FileInfo).filter(FileInfo.file_id == file_info.file_id).first()
                )
                if collision:
                    raise FilePolicyError(
                        "FILE_ID_COLLISION", "file id is already bound to another scope"
                    )
            if existing:
                old_path = existing.file_path
                existing.filename = file_info.filename
                existing.file_path = file_info.file_path
                existing.description = file_info.description
                existing.file_size = file_info.file_size
                existing.request_id = file_info.request_id
                existing.status = 1
                stored_filename = existing.filename
            else:
                session.add(file_info)
            await session.commit()
        if old_path:
            try:
                Path(old_path).unlink(missing_ok=True)
            except OSError:
                pass
        async with async_session_local() as session:
            result = await session.run_sync(
                lambda s: s.query(FileInfo)
                .filter(
                    FileInfo.request_id == stored_request_id,
                    FileInfo.filename == stored_filename
                )
                .first()
            )
            return result

    @staticmethod
    @timer()
    async def get_by_file_id(file_id: str) -> FileInfo:
        async with async_session_local() as session:
            state = select(FileInfo).where(FileInfo.file_id == file_id)
            result = await session.execute(state)
            return result.scalars().one_or_none()

    @staticmethod
    @timer()
    async def get_by_computed_file_id(request_id: str, file_name: str) -> FileInfo | None:
        from genie_tool.model.protocal import file_ids_for_lookup

        ids = file_ids_for_lookup(request_id, file_name)
        matches = await FileInfoOp.get_by_file_ids(ids)
        by_id = {item.file_id: item for item in matches}
        for file_id in ids:
            found = by_id.get(file_id)
            if found is not None:
                return found
        return None

    @staticmethod
    @timer()
    async def get_by_file_ids(file_ids: List[str]) -> List[FileInfo]:
        async with async_session_local() as session:
            state = select(FileInfo).where(FileInfo.file_id.in_(file_ids))
            result = await session.execute(state)
            return result.scalars().all()

    @staticmethod
    @timer()
    async def get_by_request_id_and_filename(
        request_id: str, filename: str
    ) -> FileInfo:
        async with async_session_local() as session:
            state = select(FileInfo).where(
                FileInfo.request_id == request_id,
                FileInfo.filename == filename,
            )
            result = await session.execute(state)
            return result.scalars().one_or_none()

    @staticmethod
    @timer()
    async def get_by_request_id(request_id: str) -> List[FileInfo]:
        async with async_session_local() as session:
            state = select(FileInfo).where(FileInfo.request_id == request_id)
            result = await session.execute(state)
            return result.scalars().all()


def _file_public_url() -> str:
    raw = (os.getenv("FILE_PUBLIC_BASE_URL") or "").strip()
    if not raw or raw.lower() in {"none", "null"}:
        return "/v1/file_tool"
    if raw.startswith("/"):
        return raw.rstrip("/") or "/v1/file_tool"
    if raw.lower().startswith(("http://", "https://")):
        return raw.rstrip("/")
    return "/v1/file_tool"


def _public_file_path(kind: str, file_id: str, file_name: str) -> str:
    return "/".join(
        [
            _file_public_url(),
            kind,
            quote(file_id, safe=""),
            quote(file_name, safe=""),
        ]
    )


def get_file_preview_url(file_id: str, file_name: str):
    return _public_file_path("preview", file_id, file_name)


def get_file_download_url(file_id: str, file_name: str):
    return _public_file_path("download", file_id, file_name)
