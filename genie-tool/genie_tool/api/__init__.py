# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: liumin.423
# Date:   2025/7/7
# =====================
from fastapi import APIRouter

from .tool import router as tool_router
from .file_manage import router as file_router
from .workspace_api import router as workspace_router

api_router = APIRouter(prefix="/v1")

api_router.include_router(tool_router, prefix="/tool", tags=["tool"])
api_router.include_router(file_router, prefix="/file_tool", tags=["file_manage"])
api_router.include_router(workspace_router, prefix="/workspaces", tags=["workspaces"])

