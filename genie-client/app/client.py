import os
from contextlib import asynccontextmanager
from typing import Optional, Any, List, Dict
import httpx
from mcp import ClientSession
from mcp.client.sse import sse_client
from app.logger import default_logger as logger
from app.header import HeaderEntity
from app.security import validate_safe_url

class SseClient:
    DEFAULT_TIMEOUT = 5
    DEFAULT_SSE_READ_TIMEOUT = 30
    def __init__(self, server_url: str, entity: Optional[HeaderEntity] = None):
        self.server_url = self._validate_server_url(server_url)
        self.headers = {}
        self.timeout = self.DEFAULT_TIMEOUT
        self.sse_read_timeout = self.DEFAULT_SSE_READ_TIMEOUT
        if entity is not None:
            if entity.timeout is not None: self.timeout = min(5, max(1, int(entity.timeout)))
            if entity.sse_read_timeout is not None: self.sse_read_timeout = min(30, max(1, int(entity.sse_read_timeout)))
            # Only explicitly allow non-sensitive MCP headers.
            self.headers.update({k:v for k,v in entity.headers.items() if k.lower() not in {"authorization","cookie","x-server-keys","x-genie-internal-mcp-token"}})
    @staticmethod
    def _validate_server_url(server_url: str) -> str:
        return validate_safe_url(server_url, allow_http=os.getenv("MCP_ALLOW_HTTP", "false").lower() == "true")
    @asynccontextmanager
    async def _sse_connection(self):
        streams = None; session_context = None; request_id = id(self)
        try:
            # URL was validated at construction; validate again immediately before connect for DNS rebinding defense.
            self.server_url = validate_safe_url(self.server_url, allow_http=os.getenv("MCP_ALLOW_HTTP", "false").lower() == "true")
            streams_context = sse_client(url=self.server_url, headers=self.headers, timeout=self.timeout, sse_read_timeout=self.sse_read_timeout)
            streams = await streams_context.__aenter__()
            session_context = ClientSession(*streams)
            session = await session_context.__aenter__()
            await session.initialize()
            yield session
        except httpx.HTTPStatusError as exc:
            raise Exception("MCP_AUTH_INVALID" if exc.response.status_code in (401,403) else "MCP_UNAVAILABLE") from exc
        except Exception as exc:
            raise Exception("MCP_UNAVAILABLE") from exc
        finally:
            if session_context:
                try: await session_context.__aexit__(None,None,None)
                except Exception: pass
            if streams is not None:
                try: await streams_context.__aexit__(None,None,None)
                except Exception: pass
    async def ping_server(self) -> str:
        try:
            async with self._sse_connection() as session: await session.send_ping(); return "success"
        except Exception as exc: logger.error("mcp.ping failed code=MCP_UNAVAILABLE"); raise Exception("MCP_UNAVAILABLE") from exc
    async def list_tools(self) -> List[Any]:
        try:
            async with self._sse_connection() as session:
                response=await session.list_tools(); tools=list(response.tools if hasattr(response,"tools") else [])
                if len(tools)>200: raise Exception("MCP_DISCOVERY_INVALID")
                return tools
        except Exception as exc: logger.error("mcp.list_tools failed code=MCP_UNAVAILABLE"); raise Exception("MCP_UNAVAILABLE") from exc
    async def call_tool(self, name: str, arguments: Optional[Dict[str, Any]] = None) -> Any:
        if not isinstance(name,str) or not name: raise ValueError("invalid tool name")
        if arguments is None: arguments={}
        if not isinstance(arguments,dict): raise ValueError("invalid arguments")
        try:
            async with self._sse_connection() as session: return await session.call_tool(name=name, arguments=arguments)
        except Exception as exc: logger.error("mcp.call_tool failed code=MCP_UNAVAILABLE"); raise Exception("MCP_UNAVAILABLE") from exc
    async def close(self): return None
    def __str__(self): return f"SseClient(server_url=<redacted>, timeout={self.timeout}s)"
    def __repr__(self): return f"SseClient(server_url=<redacted>, timeout={self.timeout}, sse_read_timeout={self.sse_read_timeout})"
