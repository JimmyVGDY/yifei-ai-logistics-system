"""Java Backend HTTP 异步客户端（回调 Tool Executor 等内部接口）。"""

from typing import Any, Optional

import httpx
from httpx import AsyncClient

from ai_service.config.settings import settings


def _response_preview(resp: httpx.Response) -> str:
    text = resp.text or ""
    return text[:500]


class JavaClient:
    """对 Java 后端的内部 HTTP 调用。"""

    _client: Optional[AsyncClient] = None

    @property
    def client(self) -> AsyncClient:
        if self._client is None:
            raise RuntimeError("JavaClient 未初始化，请先调用 connect()")
        return self._client

    async def connect(self) -> None:
        headers = {}
        if settings.ai_internal_shared_secret:
            headers["X-Internal-Secret"] = settings.ai_internal_shared_secret
        self._client = httpx.AsyncClient(
            base_url=settings.java_internal_url,
            timeout=httpx.Timeout(30.0),
            headers=headers,
            trust_env=False,            # 绕过 Windows 系统代理
        )

    async def close(self) -> None:
        if self._client:
            await self._client.aclose()
            self._client = None

    # ── Tool Executor ──

    async def execute_tool(self, tool_name: str, arguments: dict[str, Any],
                           user_context: dict[str, Any] = None) -> dict[str, Any]:
        """回调 Java /ai/internal/tool/execute 执行业务查询。"""
        headers = self._build_internal_headers(user_context)
        resp = await self.client.post(
            "/ai/internal/tool/execute",
            json={"toolName": tool_name, "arguments": arguments},
            headers=headers,
        )
        try:
            resp.raise_for_status()
        except httpx.HTTPStatusError as exc:
            raise RuntimeError(
                f"Java internal tool execute failed: status={resp.status_code}, body={_response_preview(resp)}"
            ) from exc
        return resp.json()

    # ── Tool Registry ──

    async def fetch_tool_registry(self, user_context: dict[str, Any] = None) -> list[dict[str, Any]]:
        """拉取当前用户可用的工具列表。"""
        headers = self._build_internal_headers(user_context)
        resp = await self.client.get("/ai/internal/tools/registry", headers=headers)
        try:
            resp.raise_for_status()
        except httpx.HTTPStatusError as exc:
            raise RuntimeError(
                f"Java internal tool registry failed: status={resp.status_code}, body={_response_preview(resp)}"
            ) from exc
        body = resp.json()
        if body.get("success") is False:
            raise RuntimeError(f"Java internal tool registry failed: {body.get('error', 'unknown error')}")
        return body.get("tools", [])

    @staticmethod
    def _build_internal_headers(user_context: dict[str, Any] = None) -> dict[str, str]:
        """构建 X-Internal-User 头，传递工具执行所需的最小用户与会话上下文。"""
        if not user_context:
            return {}
        import json as _json
        mini_ctx = {
            "userId": str(user_context.get("userId", "")),
            "userCode": str(user_context.get("userCode", "")),
            "roleCode": str(user_context.get("roleCode", "")),
            "customerId": str(user_context.get("customerId", "")),
            "loginSessionId": str(user_context.get("loginSessionId", "")),
            "conversationId": str(user_context.get("conversationId", "")),
            "permissions": user_context.get("permissions", []),
        }
        headers = {"X-Internal-User": _json.dumps(mini_ctx, ensure_ascii=False)}
        internal_secret = str(user_context.get("internalSecret") or "").strip()
        if internal_secret:
            headers["X-Internal-Secret"] = internal_secret
        return headers


java_client = JavaClient()
