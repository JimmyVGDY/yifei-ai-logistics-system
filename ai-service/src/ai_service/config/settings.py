"""应用配置，从环境变量 + config/ 目录加载。"""

import os
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class Settings:
    # ── 服务 ──
    host: str = "127.0.0.1"
    port: int = 8001
    log_level: str = "INFO"

    # ── Java 内部地址 ──
    java_internal_url: str = "http://localhost:8080"

    # ── Redis ──
    redis_url: str = "redis://localhost:6379"

    # ── Qdrant ──
    qdrant_url: str = "http://localhost:6333"
    qdrant_api_key: Optional[str] = None

    # ── Ollama (预留) ──
    ollama_url: str = "http://localhost:11434"

    # ── 模型降级 ──
    fallback_reply: str = "抱歉，AI 服务暂时不可用，请稍后重试。"
    max_agent_iterations: int = 5
    model_timeout_seconds: float = 60.0

    # ── 配置源 → 环境变量覆盖 ──
    def __post_init__(self) -> None:
        for key in self.__dataclass_fields__:
            env_val = os.getenv(key.upper())
            if env_val is not None:
                setattr(self, key, env_val)


settings = Settings()
