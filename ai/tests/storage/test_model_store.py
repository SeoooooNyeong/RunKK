"""PgModelRegistry: InMemoryModelRegistry와 같은 규약을 PostgreSQL에서도 지키는지(RUNKK_TEST_DB_URL 필요)."""

from __future__ import annotations

import pytest
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncEngine

from runkk_ai.storage.model_store import PgModelRegistry
from runkk_ai.writer.registry import ModelNotFound, NoPreviousModel


@pytest.fixture
def registry(pg_engine: AsyncEngine) -> PgModelRegistry:
    return PgModelRegistry(pg_engine)


async def test_first_registered_model_becomes_active(registry: PgModelRegistry) -> None:
    await registry.register("v1", "http://writer:8080", "통과율 1.0")

    active = await registry.active()

    assert active is not None
    assert (active.model_id, active.base_url, active.note, active.active) == (
        "v1",
        "http://writer:8080",
        "통과율 1.0",
        True,
    )
    assert active.activated_at is not None


async def test_second_model_waits_until_activated(registry: PgModelRegistry) -> None:
    await registry.register("v1", "http://writer:8080")
    await registry.register("v2", "http://writer-v2:8080")

    assert (await registry.active()).model_id == "v1"  # type: ignore[union-attr]
    assert [m.model_id for m in await registry.models()] == ["v1", "v2"]


async def test_activate_and_rollback(registry: PgModelRegistry) -> None:
    await registry.register("v1", "http://writer:8080")
    await registry.register("v2", "http://writer-v2:8080")

    await registry.activate("v2")
    assert (await registry.active()).model_id == "v2"  # type: ignore[union-attr]

    assert (await registry.rollback()).model_id == "v1"
    assert (await registry.rollback()).model_id == "v2"


async def test_only_one_row_can_be_active(registry: PgModelRegistry, pg_engine: AsyncEngine) -> None:
    """부분 유일 인덱스가 '켜진 모델은 하나'를 DB에서도 지킨다."""
    await registry.register("v1", "http://writer:8080")
    await registry.register("v2", "http://writer-v2:8080")
    await registry.activate("v2")

    async with pg_engine.connect() as conn:
        count = (await conn.execute(text("SELECT count(*) FROM analytics.llm_model WHERE active"))).scalar_one()

    assert count == 1


async def test_re_registering_updates_without_switching(registry: PgModelRegistry) -> None:
    await registry.register("v1", "http://writer:8080")
    await registry.register("v2", "http://writer-v2:8080")
    await registry.activate("v2")

    again = await registry.register("v1", "http://writer-moved:8080", "주소 변경")

    assert (again.base_url, again.note, again.active) == ("http://writer-moved:8080", "주소 변경", False)
    assert (await registry.active()).model_id == "v2"  # type: ignore[union-attr]


async def test_refusals(registry: PgModelRegistry) -> None:
    with pytest.raises(ModelNotFound):
        await registry.activate("none")

    await registry.register("v1", "http://writer:8080")
    with pytest.raises(NoPreviousModel):
        await registry.rollback()
