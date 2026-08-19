import asyncio
import io
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

_TEST_STORAGE = tempfile.TemporaryDirectory()
os.environ["FILE_SAVE_PATH"] = _TEST_STORAGE.name

from genie_tool.model.protocal import get_file_id
from genie_tool.util.file_policy import (
    DEFAULT_MAX_FILE_BYTES,
    FilePolicyError,
    copy_limited,
    normalize_file_name,
    normalize_request_id,
    safe_storage_path,
)


class FilePolicyTest(unittest.IsolatedAsyncioTestCase):
    async def test_rejects_path_like_scope_and_file_names(self) -> None:
        for value in ("../other", "/absolute", r"C:\\absolute", "a\x00b"):
            with self.assertRaises(FilePolicyError):
                normalize_request_id(value)
        for value in ("../other", "/absolute", r"C:\\absolute", "a\x00b", ".", "..", "a/../b"):
            with self.assertRaises(FilePolicyError):
                normalize_file_name(value)

    async def test_storage_path_stays_under_scope_directory(self) -> None:
        root = Path(_TEST_STORAGE.name)
        path = safe_storage_path(root, "workspace-v1-tenant", "a" * 64)
        self.assertEqual(root, path.parents[1])
        self.assertNotIn("workspace-v1-tenant", path.parts)
        with self.assertRaises(FilePolicyError):
            safe_storage_path(root, "workspace-v1-tenant", "../escape")

    async def test_stream_copy_stops_at_file_limit(self) -> None:
        chunks = [b"a" * (DEFAULT_MAX_FILE_BYTES // 2), b"b" * (DEFAULT_MAX_FILE_BYTES // 2 + 1)]

        async def read(_: int) -> bytes:
            await asyncio.sleep(0)
            return chunks.pop(0) if chunks else b""

        with self.assertRaisesRegex(FilePolicyError, "size limit"):
            await copy_limited(read, io.BytesIO())

    async def test_file_id_keeps_scope_and_filename_boundaries(self) -> None:
        self.assertNotEqual(get_file_id("a", "bc"), get_file_id("ab", "c"))
        self.assertNotEqual(get_file_id("scope-a", "same.txt"), get_file_id("scope-b", "same.txt"))
        self.assertEqual(get_file_id("scope-a", "same.txt"), get_file_id("scope-a", "same.txt"))

    async def test_file_id_lookup_falls_back_to_legacy_md5(self) -> None:
        from genie_tool.model.protocal import file_ids_for_lookup, get_file_id_legacy

        ids = file_ids_for_lookup("scope-a", "same.txt")
        self.assertEqual(ids[0], get_file_id("scope-a", "same.txt"))
        self.assertEqual(ids[1], get_file_id_legacy("scope-a", "same.txt"))
        self.assertNotEqual(ids[0], ids[1])

    async def test_public_download_resolves_stored_file_id_and_rejects_dot_names(self) -> None:
        from fastapi import HTTPException
        from genie_tool.api.file_manage import _get_route_file

        stored = SimpleNamespace(
            status=1,
            request_id="scope-a",
            filename="report.csv",
            file_path=str(Path(_TEST_STORAGE.name) / "kept.txt"),
        )
        Path(stored.file_path).write_text("ok", encoding="utf-8")
        with patch(
            "genie_tool.api.file_manage.FileInfoOp.get_by_file_id",
            new=AsyncMock(return_value=stored),
        ):
            file_info, request_id, file_name = await _get_route_file(
                "0123456789abcdef0123456789abcdef",
                "report.csv",
            )
        self.assertEqual(file_info.request_id, "scope-a")
        self.assertEqual(request_id, "scope-a")
        self.assertEqual(file_name, "report.csv")

        for value in (".", "..", "a/../b"):
            with self.assertRaises(HTTPException) as raised:
                await _get_route_file("scope-a", value)
            self.assertEqual(raised.exception.status_code, 400)

    async def test_response_uses_browser_relative_and_encoded_urls(self) -> None:
        from genie_tool.api.file_manage import _response
        from genie_tool.db.file_table_op import get_file_download_url, get_file_preview_url
        file_info = SimpleNamespace(filename="report #1?.csv", file_size=12)
        with patch.dict(
            os.environ,
            {"FILE_PUBLIC_BASE_URL": "", "FILE_SERVER_URL": "http://127.0.0.1:1601"},
            clear=False,
        ):
            response = _response(file_info, "scope value")
            self.assertEqual(
                response["downloadUrl"],
                "/v1/file_tool/download/scope%20value/report%20%231%3F.csv",
            )
            self.assertEqual(
                response["domainUrl"],
                "/v1/file_tool/preview/scope%20value/report%20%231%3F.csv",
            )
            self.assertEqual(
                get_file_download_url("scope value", "report #1?.csv"),
                response["downloadUrl"],
            )
            self.assertEqual(
                get_file_preview_url("scope value", "report #1?.csv"),
                response["domainUrl"],
            )

    async def test_get_file_rejects_record_from_another_scope(self) -> None:
        from fastapi import HTTPException
        from genie_tool.api.file_manage import get_file
        from genie_tool.model.protocal import FileRequest
        body = FileRequest(requestId="scope-a", fileName="private.txt")
        foreign = SimpleNamespace(status=1, request_id="scope-b")
        with patch(
            "genie_tool.api.file_manage.FileInfoOp.get_by_request_id_and_filename",
            new=AsyncMock(return_value=foreign),
        ):
            with self.assertRaises(HTTPException) as raised:
                await get_file(body)
        self.assertEqual(raised.exception.status_code, 404)


if __name__ == "__main__":
    unittest.main()
