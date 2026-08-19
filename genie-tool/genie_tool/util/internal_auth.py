"""Authentication boundary for calls from the authenticated backend proxy."""

from __future__ import annotations

import hmac
import os

from fastapi import Header, HTTPException


INTERNAL_FILE_TOKEN_HEADER = "X-Genie-Internal-File-Token"
INTERNAL_FILE_TOKEN_ENV = "GENIE_INTERNAL_FILE_TOKEN"


def require_internal_file_token(
    token: str | None = Header(default=None, alias=INTERNAL_FILE_TOKEN_HEADER),
) -> None:
    """Accept only a server-to-server token; browser/session headers are ignored."""

    expected = (
        os.getenv(INTERNAL_FILE_TOKEN_ENV, "").strip()
        or os.getenv("GENIE_INTERNAL_AGENT_TOKEN", "").strip()
    )
    if not expected or not token or not hmac.compare_digest(token, expected):
        raise HTTPException(
            status_code=401,
            detail={
                "code": "INTERNAL_FILE_TOKEN_INVALID",
                "message": "internal file token invalid",
            },
        )
