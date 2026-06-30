"""RAG 端点测试。"""

import pytest
from httpx import AsyncClient, ASGITransport

from ai_service.main import app
from ai_service.rag import retriever


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


@pytest.mark.asyncio
async def test_qdrant_search_uses_to_thread(monkeypatch):
    """同步 Qdrant 查询应放进线程，避免阻塞事件循环。"""
    calls = []

    class Point:
        payload = {"source": "docs/a.md", "title": "A", "content": "hello"}
        score = 0.9

    class Result:
        points = [Point()]

    class Client:
        def query_points(self, **kwargs):
            return Result()

    async def fake_to_thread(func, *args, **kwargs):
        calls.append(func)
        return func(*args, **kwargs)

    monkeypatch.setattr(retriever.qdrant_client, "_available", True)
    monkeypatch.setattr(retriever.qdrant_client, "_client", Client())
    monkeypatch.setattr(retriever.asyncio, "to_thread", fake_to_thread)

    response = await retriever.KnowledgeRetriever().search("订单")

    assert response.total == 1
    assert calls
