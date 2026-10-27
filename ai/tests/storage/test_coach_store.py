"""코치 대화 저장소 규약: 메모리와 PostgreSQL(analytics.coach_message)이 같은 시험을 통과한다."""

from __future__ import annotations

from collections.abc import AsyncIterator
from datetime import UTC, datetime, timedelta

import pytest

from runkk_ai.coach import CoachMessage, CoachMessageStore, InMemoryCoachStore
from runkk_ai.storage import PgCoachStore
from tests.storage.conftest import fresh_engine

T0 = datetime(2026, 10, 27, 4, 0, tzinfo=UTC)


@pytest.fixture(params=["memory", "postgres"])
async def store(request: pytest.FixtureRequest) -> AsyncIterator[CoachMessageStore]:
    if request.param == "memory":
        yield InMemoryCoachStore()
        return
    engine = await fresh_engine()
    yield PgCoachStore(engine)
    await engine.dispose()


def _msg(conversation: str, i: int, user: str = "u1") -> CoachMessage:
    role = "user" if i % 2 == 0 else "assistant"
    return CoachMessage(conversation, user, role, f"m{i}", T0 + timedelta(seconds=i), model="m" if i % 2 else None)


async def test_owner_is_who_opened(store: CoachMessageStore) -> None:
    assert await store.owner("c1") is None
    await store.append(_msg("c1", 0, user="u1"))
    await store.append(_msg("c1", 1, user="u1"))

    assert await store.owner("c1") == "u1"


async def test_recent_is_oldest_first_and_limited(store: CoachMessageStore) -> None:
    for i in range(6):
        await store.append(_msg("c1", i))
    await store.append(_msg("c2", 0))

    recent = await store.recent("c1", 4)

    assert [m.content for m in recent] == ["m2", "m3", "m4", "m5"]
    assert recent[1].role == "assistant"
    assert recent[1].model == "m"
    assert recent[0].at == T0 + timedelta(seconds=2)
    assert await store.recent("none", 4) == []


async def test_memory_store_drops_oldest_conversation() -> None:
    store = InMemoryCoachStore(max_conversations=1)
    await store.append(_msg("old", 0))
    await store.append(_msg("new", 0))

    assert await store.owner("old") is None
