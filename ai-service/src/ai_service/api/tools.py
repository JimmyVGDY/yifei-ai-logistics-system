"""内部端点：工具注册表 & Task 管理。"""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from ai_service.infrastructure.java_client import java_client

router = APIRouter()


class ToolExecuteRequest(BaseModel):
    tool_name: str
    arguments: dict


@router.get("/tools/registry")
async def get_tool_registry():
    """从 Java 拉取当前用户可用的工具列表（Agent 初始化时调用）。"""
    try:
        tools = await java_client.fetch_tool_registry()
        return {"data": tools}
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"无法获取工具注册表: {exc}")


@router.post("/tool/execute")
async def execute_tool(request: ToolExecuteRequest):
    """Agent 循环中回调 Java 执行业务查询。"""
    try:
        result = await java_client.execute_tool(request.tool_name, request.arguments)
        return result
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"工具执行失败: {exc}")
