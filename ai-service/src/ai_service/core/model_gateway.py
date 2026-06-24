"""模型网关：Provider 路由、降级链、重试、流式推理。"""

import asyncio
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import AsyncIterator, Optional

import httpx
import structlog
import yaml

logger = structlog.get_logger()


@dataclass
class ModelConfig:
    """单个模型实例的配置。"""
    provider_name: str
    model_name: str
    api_base: str
    api_key: Optional[str] = None
    temperature: float = 0.3
    max_tokens: int = 4096
    timeout_seconds: float = 60.0


@dataclass
class ToolCall:
    id: str
    name: str
    arguments: dict


@dataclass
class ModelResponse:
    content: str
    model: str
    provider: str
    input_tokens: int = 0
    output_tokens: int = 0
    duration_ms: float = 0
    tool_calls: list[ToolCall] = None
    finish_reason: str = "stop"

    def __post_init__(self):
        if self.tool_calls is None:
            self.tool_calls = []


class ProviderRegistry:
    """从 providers.yml 加载 Provider 配置。"""

    def __init__(self, config_path: Path):
        with open(config_path, encoding="utf-8") as f:
            data = yaml.safe_load(f)
        self.providers: list[dict] = data["providers"]
        self.fallback_chains: dict[str, list[str]] = data["fallback_chains"]

    def get_provider(self, name: str) -> Optional[dict]:
        for p in self.providers:
            if p["name"] == name:
                return p
        return None

    def resolve_model_provider(self, model_name: str) -> Optional[dict]:
        """Find which provider owns a model. Returns provider dict or None."""
        for p in self.providers:
            if model_name in p.get("models", []):
                return p
        return None

    def build_model_config(self, model_name: str, api_key: Optional[str] = None,
                           temperature: float = 0.3, max_tokens: int = 4096,
                           timeout_seconds: float = 60.0) -> Optional[ModelConfig]:
        """Build a ModelConfig from the registry by model name."""
        provider = self.resolve_model_provider(model_name)
        if provider is None:
            return None
        if not provider.get("enabled", True):
            return None
        return ModelConfig(
            provider_name=provider["name"],
            model_name=model_name,
            api_base=provider["api_base"],
            api_key=api_key,
            temperature=temperature,
            max_tokens=max_tokens,
            timeout_seconds=min(timeout_seconds, provider.get("timeout_seconds", 60)),
        )


class ModelGateway:
    """统一模型网关 —— 负责 Provider 路由、降级链、重试和流式推理。"""

    def __init__(self, registry: ProviderRegistry):
        self.registry = registry
        self._client: Optional[httpx.AsyncClient] = None

    async def connect(self) -> None:
        self._client = httpx.AsyncClient(trust_env=False, timeout=httpx.Timeout(120.0))

    async def close(self) -> None:
        if self._client:
            await self._client.aclose()
            self._client = None

    async def chat(
        self,
        system_prompt: str,
        user_prompt: str,
        task_type: str = "chat",
        api_key: Optional[str] = None,
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None,
        tools: Optional[list[dict]] = None,
    ) -> ModelResponse:
        """同步调用 LLM，自动走降级链。支持 function calling。"""
        chain = self.registry.fallback_chains.get(task_type, [])
        if not chain:
            chain = self.registry.fallback_chains.get("chat", [])

        last_error = None
        for model_name in chain:
            config = self.registry.build_model_config(
                model_name,
                api_key=api_key,
                temperature=temperature or 0.3,
                max_tokens=max_tokens or 4096,
            )
            if config is None:
                logger.debug("model_skipped", model=model_name, reason="provider_disabled")
                continue

            try:
                return await self._call(config, system_prompt, user_prompt, tools)
            except Exception as exc:
                last_error = exc
                logger.warning("model_fallback", model=model_name, error=str(exc)[:200])
                continue

        raise RuntimeError(f"所有模型调用失败 (task_type={task_type})，last_error={last_error}")

    async def chat_stream(
        self,
        system_prompt: str,
        user_prompt: str,
        task_type: str = "chat",
        api_key: Optional[str] = None,
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None,
        tools: Optional[list[dict]] = None,
    ) -> AsyncIterator[str]:
        """流式调用 LLM，yield delta tokens。支持 function calling。"""
        chain = self.registry.fallback_chains.get(task_type, [])
        if not chain:
            chain = self.registry.fallback_chains.get("chat", [])

        last_error = None
        for model_name in chain:
            config = self.registry.build_model_config(
                model_name,
                api_key=api_key,
                temperature=temperature or 0.3,
                max_tokens=max_tokens or 4096,
            )
            if config is None:
                continue

            try:
                async for delta in self._call_stream(config, system_prompt, user_prompt, tools):
                    yield delta
                return
            except Exception as exc:
                last_error = exc
                logger.warning("model_fallback_stream", model=model_name, error=str(exc)[:200])
                continue

        yield "抱歉，AI 服务暂时不可用，请稍后重试。"

    async def _call(self, config: ModelConfig, system: str, user: str,
                    tools: Optional[list[dict]] = None) -> ModelResponse:
        """HTTP call to OpenAI-compatible API. Supports function calling via tools param."""
        assert self._client is not None
        payload = {
            "model": config.model_name,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "temperature": config.temperature,
            "max_tokens": config.max_tokens,
            "stream": False,
        }
        if tools:
            payload["tools"] = tools
            payload["tool_choice"] = "auto"
        headers = {"Content-Type": "application/json"}
        if config.api_key:
            headers["Authorization"] = f"Bearer {config.api_key}"

        t0 = time.monotonic()
        resp = await self._client.post(
            f"{config.api_base}/chat/completions",
            json=payload,
            headers=headers,
            timeout=config.timeout_seconds,
        )
        resp.raise_for_status()
        body = resp.json()
        duration_ms = (time.monotonic() - t0) * 1000

        choice = body["choices"][0]
        finish = choice.get("finish_reason", "stop")
        msg = choice["message"]
        content = msg.get("content") or ""
        usage = body.get("usage", {})

        # 解析 function calling 返回的 tool_calls
        raw_tool_calls = msg.get("tool_calls") or []
        tool_calls = []
        for tc in raw_tool_calls:
            fn = tc.get("function", {})
            try:
                args = json.loads(fn.get("arguments", "{}"))
            except json.JSONDecodeError:
                args = {}
            tool_calls.append(ToolCall(
                id=tc.get("id", ""),
                name=fn.get("name", ""),
                arguments=args,
            ))

        return ModelResponse(
            content=content,
            model=config.model_name,
            provider=config.provider_name,
            input_tokens=usage.get("prompt_tokens", 0),
            output_tokens=usage.get("completion_tokens", 0),
            duration_ms=duration_ms,
            tool_calls=tool_calls,
            finish_reason=finish or "stop",
        )

    async def _call_stream(self, config: ModelConfig, system: str, user: str,
                           tools: Optional[list[dict]] = None) -> AsyncIterator[str]:
        """Streaming HTTP call. Yields delta text OR tool_call JSON events."""
        assert self._client is not None
        payload = {
            "model": config.model_name,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "temperature": config.temperature,
            "max_tokens": config.max_tokens,
            "stream": True,
            "stream_options": {"include_usage": True},
        }
        if tools:
            payload["tools"] = tools
            payload["tool_choice"] = "auto"
        headers = {"Content-Type": "application/json"}
        if config.api_key:
            headers["Authorization"] = f"Bearer {config.api_key}"

        import json as json_mod
        tool_call_buffer: dict[str, dict] = {}  # idx → {id, name, arguments_str}
        async with self._client.stream(
            "POST",
            f"{config.api_base}/chat/completions",
            json=payload,
            headers=headers,
            timeout=config.timeout_seconds,
        ) as resp:
            resp.raise_for_status()
            async for line in resp.aiter_lines():
                if not line.startswith("data: "):
                    continue
                data_str = line[6:]
                if data_str == "[DONE]":
                    break
                try:
                    chunk = json_mod.loads(data_str)
                    choices = chunk.get("choices", [])
                    if not choices:
                        continue
                    delta = choices[0].get("delta", {})
                    finish = choices[0].get("finish_reason")

                    # function calling: accumulate tool_call fragments
                    tc_deltas = delta.get("tool_calls")
                    if tc_deltas:
                        for tc_d in tc_deltas:
                            idx = tc_d.get("index", 0)
                            if idx not in tool_call_buffer:
                                tool_call_buffer[idx] = {"id": "", "name": "", "arguments": ""}
                            buf = tool_call_buffer[idx]
                            if tc_d.get("id"):
                                buf["id"] = tc_d["id"]
                            fn = tc_d.get("function", {})
                            if fn.get("name"):
                                buf["name"] += fn["name"]
                            if fn.get("arguments"):
                                buf["arguments"] += fn["arguments"]

                    # text delta
                    content = delta.get("content", "")
                    if content:
                        yield content

                    # finish_reason: tool_calls → yield parsed tool calls
                    if finish == "tool_calls" and tool_call_buffer:
                        for idx in sorted(tool_call_buffer.keys()):
                            buf = tool_call_buffer[idx]
                            try:
                                args = json_mod.loads(buf["arguments"]) if buf["arguments"] else {}
                            except json_mod.JSONDecodeError:
                                args = {}
                            yield json_mod.dumps({"tool_call": {"name": buf["name"], "arguments": args}})
                except (json_mod.JSONDecodeError, KeyError, IndexError):
                    continue


# Module-level singleton — created by lifespan
registry: Optional[ProviderRegistry] = None
gateway: Optional[ModelGateway] = None


async def init_gateway(config_path: Path) -> ModelGateway:
    global registry, gateway
    registry = ProviderRegistry(config_path)
    gateway = ModelGateway(registry)
    await gateway.connect()
    return gateway


async def shutdown_gateway() -> None:
    global gateway
    if gateway:
        await gateway.close()
        gateway = None
