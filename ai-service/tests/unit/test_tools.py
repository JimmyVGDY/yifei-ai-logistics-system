"""工具执行 + 注册表端点测试。"""

import pytest
from httpx import AsyncClient, ASGITransport

from ai_service.main import app


@pytest.fixture
def client():
    return AsyncClient(transport=ASGITransport(app=app), base_url="http://test")


@pytest.mark.asyncio
async def test_tool_registry_returns_502_when_java_down(client):
    """工具注册表在 Java 不可用时返回 502。"""
    resp = await client.get("/internal/tools/registry")
    assert resp.status_code == 502
    assert "无法获取工具注册表" in resp.json()["detail"]


@pytest.mark.asyncio
async def test_tool_execute_returns_502_when_java_down(client):
    """工具执行在 Java 不可用时返回 502。"""
    resp = await client.post("/internal/tool/execute", json={
        "tool_name": "query_orders",
        "arguments": {"customerName": "张三"},
    })
    assert resp.status_code == 502
    assert "工具执行失败" in resp.json()["detail"]


@pytest.mark.asyncio
async def test_tool_execute_validates_schema(client):
    """tool execute 拒绝不合法请求体。"""
    resp = await client.post("/internal/tool/execute", json={
        "tool_name": "test",
        # 缺少 arguments
    })
    assert resp.status_code == 422  # Pydantic validation
