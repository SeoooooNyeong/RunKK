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


async def test_purge_user_removes_every_conversation_of_that_user(store: CoachMessageStore) -> None:
    """탈퇴 파기(S15P31A207-219): 사용자의 대화를 모두 지운다. 다른 사용자의 대화는 그대로다. 다시 불러도 0."""
    for i in range(4):
        await store.append(_msg("c1", i, user="u1"))
    await store.append(_msg("c2", 0, user="u1"))
    await store.append(_msg("c3", 0, user="u2"))

    deleted = await store.purge_user("u1")

    assert deleted == 5
    assert await store.owner("c1") is None
    assert await store.recent("c1", 10) == []
    assert await store.owner("c2") is None
    assert await store.owner("c3") == "u2"
    assert await store.purge_user("u1") == 0


async def test_purge_before_removes_only_old_messages(store: CoachMessageStore) -> None:
    """보관 기한(-258): 기준 시각보다 오래된 메시지만 지운다. 다 지워진 대화는 없는 대화가 된다."""
    for i in range(4):
        await store.append(_msg("old", i))  # T0 ~ T0+3s
    await store.append(_msg("mixed", 0))
    await store.append(_msg("mixed", 10))  # T0+10s

    deleted = await store.purge_before(T0 + timedelta(seconds=5))

    assert deleted == 5
    assert await store.owner("old") is None
    assert [m.content for m in await store.recent("mixed", 10)] == ["m10"]
    assert await store.owner("mixed") == "u1"
    assert await store.purge_before(T0 + timedelta(seconds=5)) == 0
