"""Redis 异步客户端。"""

from typing import Optional

import redis.asyncio as aioredis
from redis.asyncio import Redis

from ai_service.config.settings import settings


class RedisClient:
    """封装 Redis 连接生命周期。"""

    _client: Optional[Redis] = None

    async def connect(self) -> None:
        self._client = aioredis.from_url(
            settings.redis_url,
            decode_responses=True,
            # 本地 Redis 5 不支持 RESP3，固定 RESP2 兼容 Windows 开发环境。
            protocol=2,
        )
        await self._client.ping()

    async def close(self) -> None:
        if self._client:
            await self._client.close()
            self._client = None

    @property
    def client(self) -> Redis:
        if self._client is None:
            # 调用方通过这个异常区分“服务未连接”和普通 Redis 命令失败。
            raise RedisNotConnectedError("Redis 未连接，请先调用 connect()")
        return self._client


class RedisNotConnectedError(RuntimeError):
    """Redis 未连接时抛出的异常。调用方可据此返回 503。"""


redis_client = RedisClient()
