"""코치 대화 본문 90일 파기(S15P31A207-258, 처리방침 D-436)."""

from __future__ import annotations

import asyncio
import logging
from datetime import UTC, datetime, timedelta

import pytest

from runkk_ai.coach import CoachMessage, CoachRetention, InMemoryCoachStore

NOW = datetime(2026, 11, 4, 6, 0, tzinfo=UTC)


def _msg(conversation: str, days_ago: float, user: str = "u1") -> CoachMessage:
    return CoachMessage(conversation, user, "user", "질문", NOW - timedelta(days=days_ago))


async def test_sweep_deletes_messages_older_than_90_days(caplog: pytest.LogCaptureFixture) -> None:
    caplog.set_level(logging.INFO)
    store = InMemoryCoachStore()
    await store.append(_msg("a", 91))
    await store.append(_msg("a", 90.01))
    await store.append(_msg("b", 89.99))
    await store.append(_msg("c", 1))
    retention = CoachRetention(store, clock=lambda: NOW)

    assert await retention.sweep() == 2
    assert await store.owner("a") is None
    assert await store.owner("b") == "u1"
    assert "coach_retention result=ok deleted=2" in caplog.text


async def test_sweep_failure_is_logged_not_raised(caplog: pytest.LogCaptureFixture) -> None:
    class Broken(InMemoryCoachStore):
        async def purge_before(self, cutoff: datetime) -> int:
            raise RuntimeError("db down postgresql://secret")

    retention = CoachRetention(Broken(), clock=lambda: NOW)

    assert await retention.sweep() == 0
    assert "coach_retention result=error detail=RuntimeError" in caplog.text
    assert "secret" not in caplog.text


async def test_start_sweeps_now_and_then_every_interval() -> None:
    calls: list[datetime] = []

    class Counting(InMemoryCoachStore):
        async def purge_before(self, cutoff: datetime) -> int:
            calls.append(cutoff)
            return 0

    retention = CoachRetention(Counting(), clock=lambda: NOW, interval_s=0.02)
    retention.start()
    await asyncio.sleep(0.07)
    await retention.stop()

    assert len(calls) >= 3
    assert calls[0] == NOW - timedelta(days=90)
    assert retention._task is not None
    assert retention._task.done()


def test_days_must_be_positive() -> None:
    with pytest.raises(ValueError, match="보관 일수"):
        CoachRetention(InMemoryCoachStore(), days=0)
