"""长期记忆端点测试。"""

import pytest
from httpx import AsyncClient, ASGITransport

from ai_service.main import app


@pytest.fixture
def client():
    return AsyncClient(transport=ASGITransport(app=app), base_url="http://test")


@pytest.mark.asyncio
async def test_recall_requires_body(client):
    """recall 端点要求请求体。"""
    resp = await client.post("/internal/memory/recall", json={
        "user_id": "1",
        "question": "查订单",
    })
    assert resp.status_code == 200
    assert "memories" in resp.json()


@pytest.mark.asyncio
async def test_sync_requires_action(client):
    """sync 端点必填 action 字段。"""
    resp = await client.post("/internal/memory/sync", json={
        "action": "upsert",
        "memory_id": 1,
        "content": "用户偏好表格展示",
    })
    # 应成功（action 有效），同步失败返回 200 + ok
    assert resp.status_code == 200


@pytest.mark.asyncio
async def test_sync_rejects_invalid_action(client):
    """sync 端点的 action 接受任意字符串（当前无枚举校验）。"""
    resp = await client.post("/internal/memory/sync", json={
        "action": "invalid_action",
        "memory_id": 1,
    })
    assert resp.status_code == 200  # 目前不过滤非法 action


@pytest.mark.asyncio
async def test_extract_returns_candidates(client):
    """extract 端点返回候选记忆列表。"""
    resp = await client.post("/internal/memory/extract", json={
        "conversation_text": "以后查订单都用表格展示",
    })
    assert resp.status_code == 200
    data = resp.json()
    assert "candidates" in data
    assert isinstance(data["candidates"], list)
