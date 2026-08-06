import json
import os
from contextlib import asynccontextmanager
from typing import Optional, Any, List, Dict

import httpcore
import httpx
from mcp import ClientSession
from mcp.client.sse import sse_client
from app.logger import default_logger as logger
from app.header import HeaderEntity
from app.security import resolve_safe_addresses, validate_safe_url

MAX_RESPONSE_BYTES = 2 * 1024 * 1024
MAX_SCHEMA_BYTES = 256 * 1024
MAX_ARGUMENT_BYTES = 256 * 1024
MAX_TOOLS = 200


class McpClientError(Exception):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


class _PinnedBackend(httpcore.AsyncNetworkBackend):
    """Resolve and dial the verified IP, while retaining the original TLS host/SNI."""
    def __init__(self):
        self._delegate = httpcore.AnyIOBackend()

    async def connect_tcp(self, origin, timeout=None, local_address=None, socket_options=None):
        addresses = resolve_safe_addresses(origin.host.decode() if isinstance(origin.host, bytes) else origin.host, origin.port)
        last_error = None
        for address in addresses:
            try:
                return await self._delegate.connect_tcp(address, origin.port, timeout, local_address, socket_options)
            except Exception as exc:  # try another already-validated address
                last_error = exc
        raise McpClientError("MCP_UNAVAILABLE") from last_error

    async def connect_unix_socket(self, path, timeout=None, socket_options=None):
        raise McpClientError("MCP_URL_REJECTED")

    async def sleep(self, seconds=0):
        await self._delegate.sleep(seconds)


class _LimitedStream(httpx.AsyncByteStream):
    def __init__(self, stream, limit: int):
        self.stream = stream
        self.limit = limit
        self.total = 0

    async def __aiter__(self):
        async for chunk in self.stream:
            self.total += len(chunk)
            if self.total > self.limit:
                raise McpClientError("MCP_RESPONSE_TOO_LARGE")
            yield chunk

    async def aclose(self):
        await self.stream.aclose()


class _PinnedTransport(httpx.AsyncBaseTransport):
    def __init__(self):
        self.pool = httpcore.AsyncConnectionPool(network_backend=_PinnedBackend(), max_connections=20)

    async def handle_async_request(self, request: httpx.Request) -> httpx.Response:
        core_request = httpcore.Request(
            method=request.method,
            url=httpcore.URL(
                scheme=request.url.raw_scheme,
                host=request.url.raw_host,
                port=request.url.port,
                target=request.url.raw_path,
            ),
            headers=request.headers.raw,
            content=request.stream,
            extensions=request.extensions,
        )
        response = await self.pool.handle_async_request(core_request)
        return httpx.Response(
            response.status,
            headers=response.headers,
            stream=_LimitedStream(response.stream, MAX_RESPONSE_BYTES),
            extensions=response.extensions,
            request=request,
        )

    async def aclose(self):
        await self.pool.aclose()


def _httpx_client_factory(headers=None, auth=None):
    # Explicitly disable redirects and environment proxies for every MCP request.
    return httpx.AsyncClient(
        headers=headers,
        auth=auth,
        transport=_PinnedTransport(),
        follow_redirects=False,
        trust_env=False,
    )


class SseClient:
    DEFAULT_TIMEOUT = 5
    DEFAULT_SSE_READ_TIMEOUT = 30

    def __init__(self, server_url: str, entity: Optional[HeaderEntity] = None):
        self.server_url = self._validate_server_url(server_url)
        self.headers = {}
        self.timeout = self.DEFAULT_TIMEOUT
        self.sse_read_timeout = self.DEFAULT_SSE_READ_TIMEOUT
        if entity is not None:
            if entity.timeout is not None:
                self.timeout = min(5, max(1, int(entity.timeout)))
            if entity.sse_read_timeout is not None:
                self.sse_read_timeout = min(30, max(1, int(entity.sse_read_timeout)))
            self.headers.update({k: v for k, v in entity.headers.items()
                                 if k.lower() not in {"authorization", "cookie", "x-server-keys", "x-genie-internal-mcp-token"}})

    @staticmethod
    def _validate_server_url(server_url: str) -> str:
        return validate_safe_url(server_url, allow_http=os.getenv("MCP_ALLOW_HTTP", "false").lower() == "true")

    @staticmethod
    def _json_size(value: Any) -> int:
        return len(json.dumps(value, ensure_ascii=False, separators=(",", ":"), default=str).encode("utf-8"))

    @staticmethod
    def _schema_size(tool: Any) -> int:
        schema = getattr(tool, "inputSchema", getattr(tool, "input_schema", None))
        if schema is None and hasattr(tool, "model_dump"):
            data = tool.model_dump(by_alias=True)
            schema = data.get("inputSchema", data.get("input_schema"))
        return SseClient._json_size(schema or {})

    @asynccontextmanager
    async def _sse_connection(self):
        streams = None
        session_context = None
        try:
            # Resolve and validate immediately before the transport opens its socket.
            self.server_url = self._validate_server_url(self.server_url)
            streams_context = sse_client(
                url=self.server_url,
                headers=self.headers,
                timeout=self.timeout,
                sse_read_timeout=self.sse_read_timeout,
                httpx_client_factory=_httpx_client_factory,
            )
            streams = await streams_context.__aenter__()
            session_context = ClientSession(*streams)
            session = await session_context.__aenter__()
            yield session
        except httpx.HTTPStatusError as exc:
            if exc.response.status_code in (301, 302, 303, 307, 308):
                raise McpClientError("MCP_REDIRECT_REJECTED") from exc
            raise McpClientError("MCP_AUTH_INVALID" if exc.response.status_code in (401, 403) else "MCP_UNAVAILABLE") from exc
        except McpClientError:
            raise
        except Exception as exc:
            raise McpClientError("MCP_UNAVAILABLE") from exc
        finally:
            if session_context:
                try:
                    await session_context.__aexit__(None, None, None)
                except Exception:
                    pass
            if streams is not None:
                try:
                    await streams_context.__aexit__(None, None, None)
                except Exception:
                    pass

    async def ping_server(self) -> str:
        try:
            async with self._sse_connection() as session:
                await session.send_ping()
                return "success"
        except McpClientError as exc:
            logger.error(f"mcp.ping failed code={exc.code}")
            raise
        except Exception as exc:
            logger.error("mcp.ping failed code=MCP_UNAVAILABLE")
            raise McpClientError("MCP_UNAVAILABLE") from exc

    async def list_tools(self) -> List[Any]:
        try:
            async with self._sse_connection() as session:
                response = await session.list_tools()
                tools = list(response.tools if hasattr(response, "tools") else [])
                if len(tools) > MAX_TOOLS or any(self._schema_size(tool) > MAX_SCHEMA_BYTES for tool in tools):
                    raise McpClientError("MCP_DISCOVERY_INVALID")
                return tools
        except McpClientError as exc:
            logger.error(f"mcp.list_tools failed code={exc.code}")
            raise
        except Exception as exc:
            logger.error("mcp.list_tools failed code=MCP_UNAVAILABLE")
            raise McpClientError("MCP_UNAVAILABLE") from exc

    async def call_tool(self, name: str, arguments: Optional[Dict[str, Any]] = None) -> Any:
        if not isinstance(name, str) or not name:
            raise McpClientError("MCP_INVALID_INPUT")
        arguments = {} if arguments is None else arguments
        if not isinstance(arguments, dict) or self._json_size(arguments) > MAX_ARGUMENT_BYTES:
            raise McpClientError("MCP_INVALID_INPUT")
        try:
            async with self._sse_connection() as session:
                return await session.call_tool(name=name, arguments=arguments)
        except McpClientError as exc:
            logger.error(f"mcp.call_tool failed code={exc.code}")
            raise
        except Exception as exc:
            logger.error("mcp.call_tool failed code=MCP_UNAVAILABLE")
            raise McpClientError("MCP_UNAVAILABLE") from exc

    async def close(self):
        return None

    def __str__(self):
        return f"SseClient(server_url=<redacted>, timeout={self.timeout}s)"

    def __repr__(self):
        return f"SseClient(server_url=<redacted>, timeout={self.timeout}, sse_read_timeout={self.sse_read_timeout})"
