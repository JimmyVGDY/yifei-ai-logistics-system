"""健康检查端点测试。"""

import pytest
from httpx import AsyncClient, ASGITransport

from ai_service.api import health as health_module
from ai_service.main import app


@pytest.fixture
def client():
    return AsyncClient(transport=ASGITransport(app=app), base_url="http://test")


@pytest.mark.asyncio
async def test_health_returns_degraded_when_infra_unavailable(client):
    """基础设施不可用时 /health 返回 degraded 而非 500。"""
    resp = await client.get("/health")
    assert resp.status_code == 200
    data = resp.json()
    assert "status" in data
    assert "checks" in data
    assert "redis" in data["checks"]
    assert "qdrant" in data["checks"]
    assert "java" in data["checks"]


@pytest.mark.asyncio
async def test_health_uses_short_java_probe_timeout(monkeypatch):
    """Java health probe should not block the Python health endpoint for the default client timeout."""

    class FakeRedisClient:
        async def ping(self):
            return True

    class FakeJavaHttpClient:
        def __init__(self):
            self.timeout = None

        async def get(self, path, timeout=None):
            self.timeout = timeout
            return {"path": path}

    fake_java = FakeJavaHttpClient()
    monkeypatch.setattr(health_module.redis_client, "_client", FakeRedisClient())
    monkeypatch.setattr(health_module.qdrant_client, "_available", True)
    monkeypatch.setattr(health_module.java_client, "_client", fake_java)

    data = await health_module.health()

    assert data["status"] == "ok"
    assert fake_java.timeout == health_module.JAVA_HEALTH_TIMEOUT_SECONDS


@pytest.mark.asyncio
async def test_metrics_endpoint(client):
    """Prometheus /metrics 端点可访问。"""
    resp = await client.get("/metrics")
    assert resp.status_code == 200
    # 必须包含 python_ 前缀指标
    assert "python_" in resp.text or "process_" in resp.text


@pytest.mark.asyncio
async def test_openapi_docs(client):
    """OpenAPI 文档自动生成。"""
    resp = await client.get("/openapi.json")
    assert resp.status_code == 200
    data = resp.json()
    assert "paths" in data
    # 核心端点存在
    assert "/chat/stream" in data["paths"]
    assert "/internal/tool/execute" in data["paths"]
    assert "/internal/tasks/{task_id}/status" in data["paths"]
