"""기동 때 analytics 테이블을 최신으로 맞춘다(alembic upgrade head)."""

from __future__ import annotations

from pathlib import Path

from alembic import command
from alembic.config import Config
from sqlalchemy.engine import Connection
from sqlalchemy.ext.asyncio import AsyncEngine

MIGRATIONS = Path(__file__).resolve().parent / "migrations"


def _upgrade(connection: Connection) -> None:
    config = Config()
    config.set_main_option("script_location", str(MIGRATIONS))
    config.attributes["connection"] = connection
    command.upgrade(config, "head")


async def upgrade(engine: AsyncEngine) -> None:
    """연결에 실패하면 예외가 그대로 올라가 기동이 멈춘다(설정이 틀린 채로 메모리 없이 뜨지 않게)."""
    async with engine.begin() as connection:
        await connection.run_sync(_upgrade)
