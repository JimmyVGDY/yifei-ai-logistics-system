"""Java Backend HTTP 异步客户端（回调 Tool Executor 等内部接口）。"""

from typing import Any, Optional

import httpx
from httpx import AsyncClient

from ai_service.config.settings import settings


class JavaClient:
    """对 Java 后端的内部 HTTP 调用。"""

    _client: Optional[AsyncClient] = None

    @property
    def client(self) -> AsyncClient:
        if self._client is None:
            raise RuntimeError("JavaClient 未初始化，请先调用 connect()")
        return self._client

    async def connect(self) -> None:
        self._client = httpx.AsyncClient(
            base_url=settings.java_internal_url,
            timeout=httpx.Timeout(30.0),
            trust_env=False,            # 绕过 Windows 系统代理
        )

    async def close(self) -> None:
        if self._client:
            await self._client.aclose()
            self._client = None

    # ── Tool Executor ──

    async def execute_tool(self, tool_name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        """回调 Java /ai/internal/tool/execute 执行业务查询。"""
        resp = await self.client.post(
            "/ai/internal/tool/execute",
            json={"toolName": tool_name, "arguments": arguments},
        )
        resp.raise_for_status()
        return resp.json()["data"]

    # ── Tool Registry ──

    async def fetch_tool_registry(self) -> list[dict[str, Any]]:
        """拉取当前用户可用的工具列表（含 name、description、parameters Schema）。"""
        resp = await self.client.get("/ai/internal/tools/registry")
        resp.raise_for_status()
        return resp.json()["data"]


java_client = JavaClient()
