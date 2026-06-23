"""健康检查。"""

from fastapi import APIRouter

from ai_service.infrastructure.redis_client import redis_client
from ai_service.infrastructure.qdrant_client import qdrant_client
from ai_service.infrastructure.java_client import java_client

router = APIRouter()


@router.get("/health")
async def health() -> dict:
    """基础设施连通性检查。"""
    checks = {}

    try:
        await redis_client.client.ping()
        checks["redis"] = "ok"
    except Exception:
        checks["redis"] = "unreachable"

    checks["qdrant"] = "ok" if qdrant_client.available else "unavailable"

    try:
        await java_client.client.get("/ai/internal/health")
        checks["java"] = "ok"
    except Exception:
        checks["java"] = "unreachable"

    all_ok = all(v in ("ok",) for v in checks.values())
    return {"status": "ok" if all_ok else "degraded", "checks": checks}
