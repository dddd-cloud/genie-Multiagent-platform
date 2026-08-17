"""Validate MCP URLs against JoyAgent McpUrlPolicy + HttpMcpClientAdapter routing."""

import re

MAX_LENGTH = 2048


def is_numeric_host(host):
    return bool(
        re.fullmatch(r"[0-9a-fA-FxX:.]+", host)
        or re.fullmatch(r"[0-9]+", host)
        or re.fullmatch(r"0x[0-9a-fA-F]+", host)
    )


def main(input):
    payload = input if isinstance(input, dict) else {"url": str(input)}
    url = (payload.get("url") or payload.get("serverUrl") or "").strip()
    errors = []
    warnings = []
    if not url:
        errors.append("url is required")
        return {"ok": False, "errors": errors, "warnings": warnings, "route": None}
    if len(url) > MAX_LENGTH:
        errors.append("URL longer than 2048 characters")
    scheme = ""
    host = ""
    rest = url
    if "://" in url:
        scheme, rest = url.split("://", 1)
        scheme = scheme.lower()
    else:
        errors.append("scheme required; JoyAgent only accepts https")
    if scheme and scheme != "https":
        errors.append("scheme must be https (http is test-profile only)")
    if "@" in rest.split("/", 1)[0]:
        errors.append("userinfo rejected")
    if "#" in url:
        errors.append("fragment rejected")
    hostport = rest.split("/", 1)[0].split("?", 1)[0]
    if ":" in hostport and not hostport.startswith("["):
        host = hostport.rsplit(":", 1)[0]
    else:
        host = hostport.strip("[]")
    if not host:
        errors.append("host required")
    elif host.lower() == "localhost":
        errors.append("localhost rejected")
    elif is_numeric_host(host):
        errors.append("raw numeric IP rejected")
    lower = url.lower()
    route = "genie-client-sse" if ("/sse" in lower or lower.endswith("sse")) else "jsonrpc-post"
    if route == "genie-client-sse":
        warnings.append(
            "URL matches prefersGenieClient(); backend forwards to genie-client with X-Genie-Internal-Mcp-Token"
        )
    else:
        warnings.append(
            "URL will be JSON-RPC POSTed tools/list from HttpMcpClientAdapter; SSE-only hosts may fail until /sse is used"
        )
    return {
        "ok": len(errors) == 0,
        "errors": errors,
        "warnings": warnings,
        "host": host,
        "route": route,
        "sourceRules": [
            "McpUrlPolicy.java",
            "DnsAddressPolicy.java",
            "HttpMcpClientAdapter.java",
        ],
    }
