"""ResultRepository 규약 시험. 메모리 저장소와 PostgreSQL 저장소가 같은 시험을 통과해야 한다.

PostgreSQL 쪽은 RUNKK_TEST_DB_URL이 있을 때만 돈다(없으면 건너뛴다).
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from datetime import UTC, datetime, timedelta

import pytest

from runkk_ai.runbody import InMemoryResultRepository, ResultRepository, SessionIn, SessionRecord, analyze_session
from runkk_ai.runbody.repository import with_status
from runkk_ai.runbody.schemas import SessionResultOut
from runkk_ai.storage import PgResultRepository
from tests.runbody.conftest import load_body
from tests.storage.conftest import fresh_engine

T0 = datetime(2026, 10, 19, 10, 0, tzinfo=UTC)


@pytest.fixture(scope="module")
def result() -> SessionResultOut:
    return analyze_session(SessionIn.model_validate(load_body("second_run")), [])


@pytest.fixture(params=["memory", "postgres"])
async def repo(request: pytest.FixtureRequest) -> AsyncIterator[ResultRepository]:
    if request.param == "memory":
        yield InMemoryResultRepository()
        return
    engine = await fresh_engine()
    yield PgResultRepository(engine)
    await engine.dispose()


def _pending(session_id: str, user_id: str = "u1", minutes: int = 0) -> SessionRecord:
    return SessionRecord(
        session_id=session_id,
        user_id=user_id,
        started_at=T0 + timedelta(minutes=minutes),
        status="pending",
        updated_at=T0,
    )


async def _done(repo: ResultRepository, result: SessionResultOut, session_id: str, minutes: int) -> None:
    pending = _pending(session_id, minutes=minutes)
    await repo.claim(pending)
    await repo.save(with_status(pending, "done", T0 + timedelta(minutes=minutes, seconds=5), result=result))


async def test_claim_new_session_returns_given_record(repo: ResultRepository) -> None:
    pending = _pending("s1")

    claimed = await repo.claim(pending)

    assert claimed == pending
    stored = await repo.get("s1")
    assert stored is not None
    assert (stored.status, stored.user_id, stored.started_at) == ("pending", "u1", T0)


async def test_claim_keeps_existing_until_failed(repo: ResultRepository) -> None:
    first = _pending("s1")
    await repo.claim(first)

    again = await repo.claim(_pending("s1", user_id="u2"))
    assert (again.user_id, again.status) == ("u1", "pending")

    await repo.save(with_status(first, "failed", T0, error_code="ENGINE_ERROR"))
    retry = _pending("s1", user_id="u2")
    assert await repo.claim(retry) == retry
    stored = await repo.get("s1")
    assert stored is not None
    assert (stored.user_id, stored.status, stored.error_code) == ("u2", "pending", None)


async def test_done_result_round_trips(repo: ResultRepository, result: SessionResultOut) -> None:
    await _done(repo, result, "s1", minutes=0)

    stored = await repo.get("s1")

    assert stored is not None
    assert stored.status == "done"
    assert stored.result == result
    assert stored.updated_at == T0 + timedelta(seconds=5)


async def test_failed_keeps_error_code(repo: ResultRepository) -> None:
    pending = _pending("s1")
    await repo.claim(pending)
    await repo.save(with_status(pending, "failed", T0, error_code="CANCELLED"))

    stored = await repo.get("s1")

    assert stored is not None
    assert (stored.status, stored.error_code, stored.result) == ("failed", "CANCELLED", None)


async def test_get_unknown_is_none(repo: ResultRepository) -> None:
    assert await repo.get("nope") is None


async def test_history_is_done_only_oldest_first_and_limited(repo: ResultRepository, result: SessionResultOut) -> None:
    for i in range(5):
        await _done(repo, result, f"s{i}", minutes=i)
    await repo.claim(_pending("still-pending", minutes=1))

    history = await repo.history("u1", before=T0 + timedelta(minutes=4), limit=3)

    assert len(history) == 3  # s1·s2·s3 (s4는 before와 같아 빠지고 pending은 세지 않는다)
    assert await repo.history("u2", before=T0 + timedelta(days=1), limit=3) == []


async def test_results_between_is_half_open(repo: ResultRepository, result: SessionResultOut) -> None:
    for i in range(3):
        await _done(repo, result, f"s{i}", minutes=i * 10)
    await repo.claim(_pending("still-pending", minutes=5))

    found = await repo.results_between("u1", T0, T0 + timedelta(minutes=20))

    assert len(found) == 2  # 0분·10분. 20분은 끝값이라 빠진다


async def test_first_started_at_counts_done_only(repo: ResultRepository, result: SessionResultOut) -> None:
    await repo.claim(_pending("early-pending", minutes=-60))
    await _done(repo, result, "s1", minutes=0)
    await _done(repo, result, "s2", minutes=30)

    assert await repo.first_started_at("u1") == T0
    assert await repo.first_started_at("u2") is None


async def test_purge_user_removes_only_that_user(repo: ResultRepository, result: SessionResultOut) -> None:
    """탈퇴 파기(S15P31A207-219): 그 사용자의 결과(진행 중 포함)만 지운다. 다른 사용자는 그대로, 다시 불러도 0."""
    await _done(repo, result, "s1", 0)
    await _done(repo, result, "s2", 10)
    await repo.claim(_pending("s3", minutes=20))  # 아직 pending
    other = _pending("o1", user_id="u2")
    await repo.claim(other)
    await repo.save(with_status(other, "done", T0, result=result))

    deleted = await repo.purge_user("u1")

    assert deleted == 3
    assert await repo.get("s1") is None
    assert await repo.get("s3") is None
    assert await repo.history("u1", T0 + timedelta(days=1), 10) == []
    assert await repo.first_started_at("u1") is None
    assert await repo.get("o1") is not None
    assert await repo.purge_user("u1") == 0
    assert await repo.purge_user("nobody") == 0
