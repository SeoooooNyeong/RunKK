"""PostgreSQL 연결 만들기."""

from __future__ import annotations

from sqlalchemy.ext.asyncio import AsyncEngine, create_async_engine

POOL_SIZE = 5  # 분석 스레드 2개 + 조회 몇 개. 같은 DB를 쓰는 api 풀(50)과 max_connections 100을 함께 지킨다
POOL_MAX_OVERFLOW = 2
_PLAIN_SCHEMES = ("postgresql://", "postgres://")
_ASYNC_SCHEME = "postgresql+asyncpg://"


def async_url(url: str) -> str:
    """postgresql://… 주소도 받는다. asyncpg 드라이버 주소로 바꿔 쓴다."""
    for scheme in _PLAIN_SCHEMES:
        if url.startswith(scheme):
            return _ASYNC_SCHEME + url[len(scheme) :]
    return url


def create_engine(url: str) -> AsyncEngine:
    return create_async_engine(
        async_url(url),
        pool_size=POOL_SIZE,
        max_overflow=POOL_MAX_OVERFLOW,
        pool_pre_ping=True,  # DB 재시작 뒤 끊긴 연결을 버리고 다시 잡는다
    )
