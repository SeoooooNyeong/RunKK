"""RunbodyService·InMemoryResultRepository 시험: 종료 정리, 저장소 상한, 이전 기록 조회."""

from __future__ import annotations

import asyncio
from datetime import UTC, datetime, timedelta

from runkk_ai.runbody import InMemoryResultRepository, RunbodyService, SessionIn, SessionRecord, analyze_session
from runkk_ai.runbody.service import analyze_in_thread
from tests.runbody.conftest import FakeAnalyzer

T0 = datetime(2026, 10, 19, 10, 0, tzinfo=UTC)


def _record(session_id: str, user_id: str = "u1", minutes: int = 0) -> SessionRecord:
    return SessionRecord(
        session_id=session_id,
        user_id=user_id,
        started_at=T0 + timedelta(minutes=minutes),
        status="pending",
        updated_at=T0,
    )


async def test_close_marks_running_analysis_failed(second_run: SessionIn) -> None:
    """종료 때 계산 중이던 세션은 pending으로 남지 않고 CANCELLED 실패가 된다(api가 다시 맡길 수 있게)."""
    analyzer = FakeAnalyzer(gate=asyncio.Event())
    service = RunbodyService(InMemoryResultRepository(), analyzer=analyzer)
    await service.accept(second_run)
    await asyncio.sleep(0)

    await service.aclose()

    record = await service.get(second_run.sessionId)
    assert record is not None
    assert (record.status, record.error_code) == ("failed", "CANCELLED")


async def test_thread_analyzer_matches_engine(first_run: SessionIn) -> None:
    in_thread = await analyze_in_thread(first_run, [])

    assert in_thread == analyze_session(first_run, [])


async def test_wait_for_unknown_session_returns() -> None:
    service = RunbodyService(InMemoryResultRepository())

    await service.wait("nope")
    await service.aclose()


async def test_claim_keeps_existing_until_failed() -> None:
    repo = InMemoryResultRepository()
    first = _record("s1")
    assert await repo.claim(first) is first

    again = _record("s1", user_id="u2")
    assert await repo.claim(again) is first

    await repo.save(SessionRecord("s1", "u1", T0, "failed", T0, error_code="ENGINE_ERROR"))
    assert await repo.claim(again) is again


async def test_repository_drops_oldest_over_limit() -> None:
    repo = InMemoryResultRepository(max_records=2)
    for i in range(3):
        await repo.claim(_record(f"s{i}"))

    assert await repo.get("s0") is None
    assert await repo.get("s2") is not None


async def test_history_is_oldest_first_and_limited(first_run: SessionIn) -> None:
    repo = InMemoryResultRepository()
    result = analyze_session(first_run, [])
    for i in range(5):
        record = _record(f"s{i}", minutes=i)
        await repo.save(SessionRecord(record.session_id, "u1", record.started_at, "done", T0, result=result))
    await repo.save(_record("pending", minutes=1))

    history = await repo.history("u1", before=T0 + timedelta(minutes=4), limit=3)

    assert len(history) == 3
    assert await repo.history("u2", before=T0 + timedelta(days=1), limit=3) == []
