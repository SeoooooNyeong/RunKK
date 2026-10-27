"""PostgreSQL 저장소만의 시험: 마이그레이션, 상태 기록, 동시 접수, 뷰, 서비스 연결(RUNKK_TEST_DB_URL 필요)."""

from __future__ import annotations

import asyncio
from datetime import UTC, datetime, timedelta
from typing import Any

import pytest
from alembic import command
from alembic.autogenerate import compare_metadata
from alembic.config import Config
from alembic.migration import MigrationContext
from sqlalchemy import Connection, text
from sqlalchemy.ext.asyncio import AsyncEngine

from runkk_ai.runbody import MonthlyService, RunbodyService, SessionIn, SessionRecord, analyze_session
from runkk_ai.runbody.repository import with_status
from runkk_ai.storage import PgResultRepository, upgrade
from runkk_ai.storage.engine import async_url
from runkk_ai.storage.migrate import MIGRATIONS
from runkk_ai.storage.tables import metadata
from tests.runbody.conftest import load_body

T0 = datetime(2026, 10, 19, 10, 0, tzinfo=UTC)


def _pending(session_id: str) -> SessionRecord:
    return SessionRecord(session_id=session_id, user_id="u1", started_at=T0, status="pending", updated_at=T0)


async def _rows(engine: AsyncEngine, sql: str) -> list[Any]:
    async with engine.connect() as conn:
        return list((await conn.execute(text(sql))).all())


def _diff(connection: Connection) -> list[Any]:
    context = MigrationContext.configure(
        connection, opts={"include_schemas": True, "version_table_schema": "analytics"}
    )
    return [d for d in compare_metadata(context, metadata) if "alembic_version" not in str(d)]


async def test_migration_matches_table_definitions(pg_engine: AsyncEngine) -> None:
    """tables.py(쿼리용)와 마이그레이션(실제 DB)이 어긋나면 실패한다."""
    async with pg_engine.connect() as conn:
        assert await conn.run_sync(_diff) == []


async def test_upgrade_twice_is_noop(pg_engine: AsyncEngine) -> None:
    await upgrade(pg_engine)

    assert await _rows(pg_engine, "SELECT version_num FROM analytics.alembic_version") == [("v0001",)]


def _downgrade_all(connection: Connection) -> None:
    config = Config()
    config.set_main_option("script_location", str(MIGRATIONS))
    config.attributes["connection"] = connection
    command.downgrade(config, "base")


async def test_downgrade_then_upgrade_restores_schema(pg_engine: AsyncEngine) -> None:
    async with pg_engine.begin() as conn:
        await conn.run_sync(_downgrade_all)
    assert await _rows(pg_engine, "SELECT to_regclass('analytics.session_result')") == [(None,)]

    await upgrade(pg_engine)

    assert await _rows(pg_engine, "SELECT count(*) FROM analytics.v_session_result") == [(0,)]


async def test_status_changes_are_logged_as_events(pg_engine: AsyncEngine) -> None:
    repo = PgResultRepository(pg_engine)
    pending = _pending("s1")
    await repo.claim(pending)
    await repo.claim(_pending("s1"))  # 중복 접수는 기록하지 않는다
    await repo.save(with_status(pending, "failed", T0 + timedelta(seconds=1), error_code="ENGINE_ERROR"))
    await repo.claim(_pending("s1"))  # 실패 뒤 재접수

    events = await _rows(pg_engine, "SELECT status, error_code FROM analytics.analysis_event ORDER BY id")

    assert events == [("pending", None), ("failed", "ENGINE_ERROR"), ("pending", None)]


async def test_concurrent_claims_accept_exactly_one(pg_engine: AsyncEngine) -> None:
    repo = PgResultRepository(pg_engine)
    candidates = [_pending("same") for _ in range(5)]

    claimed = await asyncio.gather(*(repo.claim(c) for c in candidates))

    assert sum(1 for c, got in zip(candidates, claimed, strict=True) if got is c) == 1


async def test_view_exposes_done_results_only(pg_engine: AsyncEngine) -> None:
    repo = PgResultRepository(pg_engine)
    result = analyze_session(SessionIn.model_validate(load_body("second_run")), [])
    done = _pending("done-1")
    await repo.claim(done)
    await repo.save(with_status(done, "done", T0, result=result))
    await repo.claim(_pending("pending-1"))

    rows = await _rows(
        pg_engine,
        "SELECT session_id, distance_meters, pace_seconds_per_km, intensity_score, headline, rule_id, coaching_flags"
        " FROM analytics.v_session_result",
    )

    assert rows == [
        (
            "done-1",
            result.summary.distanceMeters,
            result.summary.paceSecondsPerKm,
            result.axes["intensity"].score,
            result.feedback.headline,
            result.feedback.ruleId,
            result.coaching.flags,
        )
    ]


async def test_service_compares_with_previous_run_from_db(pg_engine: AsyncEngine) -> None:
    """실측 2회를 차례로 분석하면 두 번째가 DB에서 첫 번째를 읽어 비교하고, 월간도 DB에서 모은다."""
    repo = PgResultRepository(pg_engine)
    service = RunbodyService(repo)
    first, second = (SessionIn.model_validate(load_body(n)) for n in ("first_run", "second_run"))
    for run in (first, second):
        await service.accept(run)
        await service.wait(run.sessionId)
    await service.aclose()

    record = await repo.get(second.sessionId)
    monthly = await MonthlyService(repo).month(second.userId, "2026-10")

    assert record is not None
    assert record.result is not None
    assert record.result.prev is not None
    assert record.result.prev.sessionId == first.sessionId
    assert monthly.sessionCount == 2


@pytest.mark.parametrize(
    ("given", "expected"),
    [
        ("postgresql://u:p@h:5432/d", "postgresql+asyncpg://u:p@h:5432/d"),
        ("postgres://u:p@h/d", "postgresql+asyncpg://u:p@h/d"),
        ("postgresql+asyncpg://u:p@h/d", "postgresql+asyncpg://u:p@h/d"),
    ],
)
def test_async_url_accepts_plain_postgres_urls(given: str, expected: str) -> None:
    assert async_url(given) == expected
