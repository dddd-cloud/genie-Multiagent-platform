from datetime import datetime
from fastapi import FastAPI, Request, Body, Depends

from app.client import SseClient
from app.header import HeaderEntity
from app.security import require_internal_mcp_token
from app.logger import default_logger as logger

app = FastAPI(
    title="Genie MCP Client API",
    version="0.1.0",
    description="A lightweight web service for Model Context Protocol (MCP) server communication",
    contact={
        "name": "Your Name/Team",
        "email": "your-email@example.com",
    },
    license_info={
        "name": "MIT",
    },
)


@app.get("/health")
async def health_check():
    """
    - 健康检查接口
    """
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "version": "0.1.0"
    }


@app.post("/v1/serv/pong")
async def ping_server(
        request: Request,
        server_url: str = Body(..., embed=True, description="mcp server url", alias="server_url"),
        _: None = Depends(require_internal_mcp_token),
):
    """
    - 根据请求 server_url 测试 server 的连通性
    """
    logger.info("mcp.pong request")
    mcp_client = SseClient(server_url=server_url, entity=HeaderEntity(request.headers))
    try:
        await mcp_client.ping_server()
        return {
            "code": 200,
            "message": "success",
            "data": {},
        }
    except Exception as e:
        logger.error("mcp.pong failed")
        return {
            "code": 500,
            "message": "MCP_UNAVAILABLE",
            "data": None,
        }


@app.post("/v1/tool/list")
async def list_tools(
        request: Request,
        server_url: str = Body(..., embed=True, description="mcp server url", alias="server_url"),
        _: None = Depends(require_internal_mcp_token),
):
    """
    - 根据请求 server_url 查询 tools 列表
    """
    logger.info("mcp.tool_list request")
    mcp_client = SseClient(server_url=server_url, entity=HeaderEntity(request.headers))
    try:
        tools = await mcp_client.list_tools()
        return {
            "code": 200,
            "message": "success",
            "data": tools,
        }
    except Exception as e:
        logger.error("mcp.tool_list failed")
        return {
            "code": 500,
            "message": "MCP_UNAVAILABLE",
            "data": None,
        }


@app.post("/v1/tool/call")
async def call_tool(
        request: Request,
        server_url: str = Body(..., description="mcp server url", alias="server_url"),
        name: str = Body(..., description="tool name to call", alias="name"),
        arguments: dict = Body(..., description="tool parameters", alias="arguments"),
        _: None = Depends(require_internal_mcp_token),
):
    """
    - 调用指定工具
    """
    logger.info("mcp.tool_call request")
    entity = HeaderEntity(request.headers)
    mcp_client = SseClient(server_url=server_url, entity=entity)
    try:
        result = await mcp_client.call_tool(name, arguments)
        return {
            "code": 200,
            "message": "success",
            "data": result,
        }
    except Exception as e:
        logger.error("mcp.tool_call failed")
        return {
            "code": 500,
            "message": "MCP_UNAVAILABLE",
            "data": None,
        }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8188)
