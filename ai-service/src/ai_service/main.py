"""
FastAPI 应用入口。

启动:
    uv run uvicorn src.ai_service.main:app --host 127.0.0.1 --port 8001 --reload

OTel 链路追踪:
    往 localhost:4318 发送 trace（本地需先启动 Jaeger all-in-one）
    环境变量 OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces
    不设时自动退到 console 输出 traceId
"""

import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from ai_service import __version__
from ai_service.infrastructure.redis_client import redis_client
from ai_service.infrastructure.qdrant_client import qdrant_client
from ai_service.infrastructure.java_client import java_client
from ai_service.config.settings import settings
from ai_service.api import chat, tools, memory, rag, tasks, health


def _setup_otel() -> None:
    """配置 OpenTelemetry SDK。未配 Jaeger 时自动退到 console 输出 traceId。"""
    from opentelemetry import trace
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.resources import SERVICE_NAME, Resource
    from opentelemetry.sdk.trace.export import BatchSpanProcessor
    from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
    from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
    from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor

    resource = Resource.create({SERVICE_NAME: "logistics-ai-service"})

    otlp_endpoint = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "")
    if otlp_endpoint:
        exporter = OTLPSpanExporter(endpoint=otlp_endpoint)
        provider = TracerProvider(resource=resource)
        provider.add_span_processor(BatchSpanProcessor(exporter))
    else:
        # 无 Jaeger → SDK 只输出 traceId 到日志
        provider = TracerProvider(resource=resource)

    trace.set_tracer_provider(provider)

    # 自动埋点
    FastAPIInstrumentor.instrument_app(app)
    HTTPXClientInstrumentor().instrument()


@asynccontextmanager
async def lifespan(application: FastAPI):
    """应用生命周期：连接基础设施 + 初始化 AI 核心服务。"""
    import structlog
    from pathlib import Path

    import ai_service.core.model_gateway as mg_module
    import ai_service.core.prompt_engine as pe_module
    from ai_service.core.prompt_engine import PromptEngine
    from ai_service.core.agent import AgentOrchestrator
    import ai_service.api.chat as chat_module

    logger = structlog.get_logger()

    # ── 基础设施 ──
    try:
        await redis_client.connect()
    except Exception:
        logger.warning("redis_unavailable", url=settings.redis_url)

    try:
        await qdrant_client.connect(settings.qdrant_url, settings.qdrant_api_key)
    except Exception:
        logger.warning("qdrant_unavailable", url=settings.qdrant_url)

    try:
        await java_client.connect()
    except Exception:
        logger.warning("java_unavailable", url=settings.java_internal_url)

    # ── AI 核心服务 ──
    _project_root = Path(__file__).parent.parent.parent
    _prompts_dir = _project_root / "prompts"
    _providers_path = _project_root / "config" / "providers.yml"

    # ModelGateway
    try:
        await mg_module.init_gateway(_providers_path)
        logger.info("model_gateway_ready", providers=_providers_path.as_posix())
    except Exception:
        logger.warning("model_gateway_init_failed")

    # PromptEngine — 设置模块级单例
    try:
        pe_module.prompt_engine = PromptEngine(_prompts_dir)
        logger.info("prompt_engine_ready", templates_dir=_prompts_dir.as_posix())
    except Exception:
        logger.warning("prompt_engine_init_failed")

    # AgentOrchestrator — inject into chat router module
    if mg_module.gateway is not None and pe_module.prompt_engine is not None:
        chat_module.orchestrator = AgentOrchestrator(mg_module.gateway, pe_module.prompt_engine)
        logger.info("agent_orchestrator_ready")
    else:
        logger.warning("orchestrator_skipped", reason="gateway_or_prompt_engine_unavailable")

    yield

    # ── 关闭 ──
    await mg_module.shutdown_gateway()
    await redis_client.close()
    await qdrant_client.close()
    await java_client.close()


app = FastAPI(
    title="物流管理 AI 服务",
    version=__version__,
    lifespan=lifespan,
)

# ── 1. 路由注册（必须在 OTel instrumentation 之前完成） ──
app.include_router(health.router, tags=["health"])
app.include_router(chat.router, prefix="/chat", tags=["chat"])
app.include_router(tools.router, prefix="/internal", tags=["internal"])
app.include_router(memory.router, prefix="/internal/memory", tags=["memory"])
app.include_router(rag.router, prefix="/internal/rag", tags=["rag"])
app.include_router(tasks.router, prefix="/internal/tasks", tags=["tasks"])

# ── 2. OTel 链路追踪 ──
_setup_otel()

# ── 3. Prometheus 指标 ──
Instrumentator().instrument(app).expose(app, endpoint="/metrics")
