"""Shared safety policy for workspace-facing file operations.

The file tool is an internal service, but its inputs still cross a browser and
multiple containers.  Keep the policy in one place so upload, generated-file
and download paths cannot drift apart.
"""

from __future__ import annotations

import hashlib
import os
import re
import unicodedata
from pathlib import Path
from typing import Awaitable, BinaryIO, Callable


DEFAULT_MAX_FILE_BYTES = 25 * 1024 * 1024
DEFAULT_MAX_FILE_NAME_LENGTH = 255
DEFAULT_MAX_REQUEST_ID_LENGTH = 255


def _positive_int(name: str, default: int) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except (TypeError, ValueError):
        return default
    return value if value > 0 else default


MAX_FILE_BYTES = _positive_int("FILE_MAX_BYTES", DEFAULT_MAX_FILE_BYTES)
MAX_FILE_NAME_LENGTH = _positive_int(
    "FILE_MAX_NAME_LENGTH", DEFAULT_MAX_FILE_NAME_LENGTH
)
MAX_REQUEST_ID_LENGTH = _positive_int(
    "FILE_MAX_REQUEST_ID_LENGTH", DEFAULT_MAX_REQUEST_ID_LENGTH
)

_CONTROL_CHARS = re.compile(r"[\x00-\x1f\x7f]")


class FilePolicyError(ValueError):
    """An input crossed a file-service safety boundary."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def normalize_request_id(value: str | None) -> str:
    normalized = unicodedata.normalize("NFC", (value or "").strip())
    if (
        not normalized
        or len(normalized) > MAX_REQUEST_ID_LENGTH
        or "/" in normalized
        or "\\" in normalized
        or _CONTROL_CHARS.search(normalized)
    ):
        raise FilePolicyError("INVALID_SCOPE", "requestId is invalid")
    return normalized


def normalize_file_name(value: str | None) -> str:
    normalized = unicodedata.normalize("NFC", (value or "").strip())
    if (
        not normalized
        or normalized in {".", ".."}
        or len(normalized) > MAX_FILE_NAME_LENGTH
        or "/" in normalized
        or "\\" in normalized
        or _CONTROL_CHARS.search(normalized)
    ):
        raise FilePolicyError("INVALID_FILE_NAME", "file name is invalid")
    return normalized


def safe_storage_path(
    root: str | os.PathLike[str], scope: str, file_id: str
) -> Path:
    """Return a deterministic path without putting user input in a pathname."""

    normalized_scope = normalize_request_id(scope)
    if not re.fullmatch(r"(?:[0-9a-f]{32}|[0-9a-f]{64})", file_id):
        raise FilePolicyError("INVALID_FILE_ID", "file id is invalid")
    scope_key = hashlib.sha256(normalized_scope.encode("utf-8")).hexdigest()
    root_path = Path(root).resolve()
    candidate = (root_path / scope_key / file_id).resolve()
    if root_path != candidate and root_path not in candidate.parents:
        raise FilePolicyError("INVALID_FILE_PATH", "file path escapes storage root")
    return candidate


async def copy_limited(
    read: Callable[[int], Awaitable[bytes]], destination: BinaryIO
) -> int:
    """Copy a Starlette upload without blocking the event loop or overflowing disk."""

    total = 0
    while True:
        chunk = await read(1024 * 1024)
        if not chunk:
            break
        total += len(chunk)
        if total > MAX_FILE_BYTES:
            raise FilePolicyError("FILE_TOO_LARGE", "file exceeds the size limit")
        destination.write(chunk)
    return total


def assert_text_size(value: str) -> None:
    if len(value.encode("utf-8")) > MAX_FILE_BYTES:
        raise FilePolicyError("FILE_TOO_LARGE", "file exceeds the size limit")
