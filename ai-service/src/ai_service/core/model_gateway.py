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
class ModelResponse:
    content: str
    model: str
    provider: str
    input_tokens: int = 0
    output_tokens: int = 0
    duration_ms: float = 0


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
    ) -> ModelResponse:
        """同步调用 LLM，自动走降级链。"""
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
                return await self._call(config, system_prompt, user_prompt)
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
    ) -> AsyncIterator[str]:
        """流式调用 LLM，yield delta tokens。自动降级链。"""
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
                async for delta in self._call_stream(config, system_prompt, user_prompt):
                    yield delta
                return  # stream succeeded
            except Exception as exc:
                last_error = exc
                logger.warning("model_fallback_stream", model=model_name, error=str(exc)[:200])
                continue

        # All models failed — yield a user-friendly fallback message
        fallback = "抱歉，AI 服务暂时不可用，请稍后重试。"
        yield fallback

    async def _call(self, config: ModelConfig, system: str, user: str) -> ModelResponse:
        """Actual HTTP call to OpenAI-compatible API."""
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
        content = choice["message"]["content"]
        usage = body.get("usage", {})

        return ModelResponse(
            content=content,
            model=config.model_name,
            provider=config.provider_name,
            input_tokens=usage.get("prompt_tokens", 0),
            output_tokens=usage.get("completion_tokens", 0),
            duration_ms=duration_ms,
        )

    async def _call_stream(self, config: ModelConfig, system: str, user: str) -> AsyncIterator[str]:
        """Streaming HTTP call. Yields delta text chunks."""
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
        headers = {"Content-Type": "application/json"}
        if config.api_key:
            headers["Authorization"] = f"Bearer {config.api_key}"

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
                import json
                try:
                    chunk = json.loads(data_str)
                    choices = chunk.get("choices", [])
                    if choices:
                        delta = choices[0].get("delta", {})
                        content = delta.get("content", "")
                        if content:
                            yield content
                except (json.JSONDecodeError, KeyError, IndexError):
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
