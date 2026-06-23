"""RAG 端点测试。"""

import pytest
from httpx import AsyncClient, ASGITransport

from ai_service.main import app


@pytest.fixture
def client():
    return AsyncClient(transport=ASGITransport(app=app), base_url="http://test")


@pytest.mark.asyncio
async def test_search_returns_results(client):
    """文档搜索返回结构化结果。"""
    resp = await client.post("/internal/rag/search", json={
        "query": "订单创建流程",
    })
    assert resp.status_code == 200
    data = resp.json()
    assert "results" in data


@pytest.mark.asyncio
async def test_reindex_returns_stats(client):
    """重建索引返回统计信息。"""
    resp = await client.post("/internal/rag/reindex", json={"paths": []})
    assert resp.status_code == 200
    data = resp.json()
    assert "indexed" in data
    assert "skipped" in data
