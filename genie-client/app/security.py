import hmac, ipaddress, os, socket
from urllib.parse import urlparse
from fastapi import Header, HTTPException
INTERNAL_MCP_HEADER = "X-Genie-Internal-Mcp-Token"
INTERNAL_MCP_ENV = "GENIE_INTERNAL_MCP_TOKEN"
def validate_safe_url(value: str, allow_http: bool | None = None) -> str:
    if not isinstance(value, str) or len(value) > 2048: raise ValueError("MCP_URL_REJECTED")
    p=urlparse(value)
    if p.scheme not in (("http","https") if allow_http else ("https",)) or not p.hostname or p.username or p.password or p.fragment: raise ValueError("MCP_URL_REJECTED")
    if p.port is not None and not 1 <= p.port <= 65535: raise ValueError("MCP_URL_REJECTED")
    if p.hostname.lower()=="localhost" or p.hostname.isdigit(): raise ValueError("MCP_URL_REJECTED")
    try:
        for info in socket.getaddrinfo(p.hostname,p.port or 443,type=socket.SOCK_STREAM):
            a=ipaddress.ip_address(info[4][0])
            if a.is_private or a.is_loopback or a.is_link_local or a.is_multicast or a.is_unspecified or a.is_reserved: raise ValueError("MCP_URL_REJECTED")
    except (OSError,ValueError): raise ValueError("MCP_URL_REJECTED")
    return value.rstrip('/')
def require_internal_mcp_token(token: str | None = Header(default=None, alias=INTERNAL_MCP_HEADER)):
    expected=os.getenv(INTERNAL_MCP_ENV,"")
    if not expected or not token or not hmac.compare_digest(token, expected): raise HTTPException(status_code=401, detail={"code":"INTERNAL_MCP_TOKEN_INVALID","message":"internal token invalid"})
