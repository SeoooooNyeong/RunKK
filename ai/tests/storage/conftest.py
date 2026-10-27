"""PostgreSQL 저장소 시험용 fixture.

RUNKK_TEST_DB_URL(analytics 스키마를 지우고 만들 수 있는 계정)이 있을 때만 PostgreSQL 시험을 돌린다.
CI는 ai:test 잡의 postgres 서비스를 쓴다. 로컬은 ai/README.md "PostgreSQL 저장소" 절.
시험마다 analytics 스키마를 지우고 마이그레이션부터 다시 만든다.
"""

from __future__ import annotations

import os
from collections.abc import AsyncIterator

import pytest
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncEngine

from runkk_ai.storage import create_engine, upgrade

TEST_DB_URL = os.environ.get("RUNKK_TEST_DB_URL")
NO_DB = "RUNKK_TEST_DB_URL이 없으면 PostgreSQL 시험을 건너뛴다"


async def fresh_engine() -> AsyncEngine:
    if not TEST_DB_URL:
        pytest.skip(NO_DB)
    engine = create_engine(TEST_DB_URL)
    async with engine.begin() as conn:
        await conn.execute(text("DROP SCHEMA IF EXISTS analytics CASCADE"))
        await conn.execute(text("CREATE SCHEMA analytics"))
    await upgrade(engine)
    return engine


@pytest.fixture
async def pg_engine() -> AsyncIterator[AsyncEngine]:
    engine = await fresh_engine()
    yield engine
    await engine.dispose()
