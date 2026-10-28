"""Alembic 실행 환경.

ai 서버는 기동 때 runkk_ai.storage.migrate.upgrade()로 연결을 넘겨 부른다(config.attributes["connection"]).
손으로 돌릴 때는 ai 폴더에서 RUNKK_DB_URL을 넣고 `uv run alembic upgrade head`(alembic.ini).
버전 표(alembic_version)도 analytics 스키마에 둔다. runkk_ai 계정은 public에 쓸 수 없다.
"""

from __future__ import annotations

import asyncio
import os

from alembic import context
from sqlalchemy.engine import Connection
from sqlalchemy.ext.asyncio import create_async_engine

from runkk_ai.storage.engine import async_url
from runkk_ai.storage.tables import SCHEMA, metadata


def _configure(connection: Connection) -> None:
    context.configure(
        connection=connection,
        target_metadata=metadata,
        version_table_schema=SCHEMA,
        include_schemas=True,
    )
    with context.begin_transaction():
        context.run_migrations()


async def _run_with_url(url: str) -> None:
    engine = create_async_engine(url)
    try:
        async with engine.connect() as connection:
            await connection.run_sync(_configure)
            await connection.commit()
    finally:
        await engine.dispose()


def _run() -> None:
    connection = context.config.attributes.get("connection")
    if connection is not None:
        _configure(connection)
        return
    url = os.environ.get("RUNKK_DB_URL")
    if not url:
        raise RuntimeError("RUNKK_DB_URL을 넣고 실행한다")
    asyncio.run(_run_with_url(async_url(url)))


if context.is_offline_mode():
    raise RuntimeError("오프라인(SQL 출력) 모드는 쓰지 않는다")
_run()
