import pathlib
import unittest
import asyncio
from contextlib import asynccontextmanager
from unittest.mock import patch
from app.client import SseClient, MAX_ARGUMENT_BYTES, MAX_SCHEMA_BYTES, MAX_RESPONSE_BYTES, MAX_TOOLS, _LimitedStream, _httpx_client_factory
from app.security import resolve_safe_addresses

class RedactionTest(unittest.TestCase):
    def test_client_sources_do_not_log_raw_transport_values(self):
        root = pathlib.Path(__file__).parents[1]
        text = '\n'.join((root / p).read_text(encoding='utf-8') for p in ('server.py','app/client.py','app/header.py'))
        self.assertNotIn('request headers:', text)
        self.assertNotIn('cookies={self.cookies}', text)
        self.assertNotIn('with arguments:', text)

    def test_limits_and_redirect_policy_are_explicit(self):
        self.assertEqual(MAX_TOOLS, 200)
        self.assertEqual(MAX_SCHEMA_BYTES, 256 * 1024)
        self.assertEqual(MAX_ARGUMENT_BYTES, 256 * 1024)
        self.assertEqual(MAX_RESPONSE_BYTES, 2 * 1024 * 1024)
        client = _httpx_client_factory()
        try:
            self.assertFalse(client.follow_redirects)
            self.assertFalse(client._trust_env)
        finally:
            asyncio.run(client.aclose())

    def test_oversized_arguments_are_rejected_before_connect(self):
        client = SseClient('https://example.com/mcp')
        with self.assertRaises(Exception) as ctx:
            asyncio.run(client.call_tool('tool', {'payload': 'x' * (MAX_ARGUMENT_BYTES + 1)}))
        self.assertIn('MCP_INVALID_INPUT', str(ctx.exception))

    def test_oversized_response_is_rejected_without_retaining_body(self):
        class Stream:
            async def __aiter__(self):
                yield b'x' * (MAX_RESPONSE_BYTES + 1)
            async def aclose(self):
                return None
        async def consume():
            async for _ in _LimitedStream(Stream(), MAX_RESPONSE_BYTES):
                pass
        with self.assertRaises(Exception) as ctx:
            asyncio.run(consume())
        self.assertIn('MCP_RESPONSE_TOO_LARGE', str(ctx.exception))

    def test_tool_count_and_schema_limits_are_enforced(self):
        class Tool:
            def __init__(self, schema): self.inputSchema = schema
        class Response:
            def __init__(self, tools): self.tools = tools
        class Session:
            def __init__(self, tools): self.tools = tools
            async def list_tools(self): return Response(self.tools)
        async def check(tools):
            client = SseClient.__new__(SseClient)
            @asynccontextmanager
            async def connection(): yield Session(tools)
            client._sse_connection = connection
            return await client.list_tools()
        with self.assertRaises(Exception):
            asyncio.run(check([Tool({}) for _ in range(MAX_TOOLS + 1)]))
        with self.assertRaises(Exception):
            asyncio.run(check([Tool({'schema': 'x' * MAX_SCHEMA_BYTES})]))

    def test_dns_rejects_mixed_safe_and_private_results(self):
        with patch('app.security.socket.getaddrinfo', return_value=[(None,None,None,None,('93.184.216.34',443)), (None,None,None,None,('127.0.0.1',443))]):
            with self.assertRaises(ValueError):
                resolve_safe_addresses('example.com', 443)

if __name__ == '__main__': unittest.main()
