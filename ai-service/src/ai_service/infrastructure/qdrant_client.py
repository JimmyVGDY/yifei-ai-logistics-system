"""Qdrant REST 客户端。"""

from typing import Optional

from qdrant_client import QdrantClient
from qdrant_client.http.exceptions import UnexpectedResponse


class QdrantClientManager:
    """封装 Qdrant 连接生命周期。不可用时降级。"""

    _client: Optional[QdrantClient] = None
    _available: bool = False

    async def connect(self, url: str, api_key: Optional[str]) -> None:
        try:
            self._client = QdrantClient(
                url=url,
                api_key=api_key,
                prefer_grpc=False,
                trust_env=False,                # Windows 系统代理会劫持 localhost
                check_compatibility=False,
                timeout=10,
            )
            self._client.get_collections()
            self._available = True
        except (UnexpectedResponse, ConnectionError, OSError, Exception):
            self._available = False

    async def close(self) -> None:
        if self._client:
            self._client.close()
            self._client = None
        self._available = False

    @property
    def client(self) -> QdrantClient:
        if not self._client:
            raise RuntimeError("Qdrant 未连接")
        return self._client

    @property
    def available(self) -> bool:
        return self._available


qdrant_client = QdrantClientManager()
