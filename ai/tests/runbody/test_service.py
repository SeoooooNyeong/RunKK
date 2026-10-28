"""RunbodyService·InMemoryResultRepository 시험: 종료 정리, 저장소 상한, 이전 기록 조회."""

from __future__ import annotations

import asyncio
from datetime import UTC, datetime, timedelta

from runkk_ai.runbody import (
    InMemoryResultRepository,
    RunbodyService,
    SessionIn,
    SessionRecord,
    SessionResultOut,
    analyze_session,
)
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
    in_thread = await analyze_in_thread(first_run, [], None)

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


async def test_results_between_is_half_open_and_done_only(first_run: SessionIn) -> None:
    repo = InMemoryResultRepository()
    result = analyze_session(first_run, [])
    for i in range(3):
        record = _record(f"s{i}", minutes=i * 10)
        await repo.save(SessionRecord(record.session_id, "u1", record.started_at, "done", T0, result=result))
    await repo.save(_record("pending", minutes=5))

    found = await repo.results_between("u1", T0, T0 + timedelta(minutes=20))

    assert len(found) == 2  # 0분·10분. 20분은 끝값이라 빠지고 pending은 세지 않는다
    assert await repo.first_started_at("u1") == T0
    assert await repo.first_started_at("u2") is None


async def test_same_user_runs_are_analyzed_in_order(first_run: SessionIn, second_run: SessionIn) -> None:
    """같은 사용자의 두 러닝을 동시에 맡겨도 두 번째가 첫 번째 결과를 이전 기록으로 본다(S15P31A207-216)."""
    analyzer = FakeAnalyzer(gate=asyncio.Event())
    service = RunbodyService(InMemoryResultRepository(), analyzer=analyzer)
    await service.accept(first_run)
    await service.accept(second_run)
    await asyncio.sleep(0)

    analyzer.gate.set()
    await service.wait(first_run.sessionId)
    await service.wait(second_run.sessionId)
    await service.aclose()

    record = await service.get(second_run.sessionId)
    assert record is not None
    assert record.result is not None
    assert record.result.prev is not None
    assert record.result.prev.sessionId == first_run.sessionId
    assert service._user_locks == {}  # 다 끝나면 사용자별 잠금을 남기지 않는다


async def test_other_users_still_run_together(first_run: SessionIn, second_run: SessionIn) -> None:
    """다른 사용자끼리는 서로 기다리지 않는다."""
    analyzer = FakeAnalyzer(gate=asyncio.Event())
    service = RunbodyService(InMemoryResultRepository(), analyzer=analyzer)
    other = second_run.model_copy(update={"userId": "u_other"})
    await service.accept(first_run)
    await service.accept(other)
    for _ in range(3):
        await asyncio.sleep(0)

    assert analyzer.calls == [first_run.sessionId, other.sessionId]
    analyzer.gate.set()
    await service.aclose()


async def test_polisher_rewrites_result_before_done(second_run: SessionIn) -> None:
    """분석 뒤 다듬기(writer)를 거친 결과가 done으로 저장된다. 앱은 한 번만 읽으면 된다."""
    seen: list[str] = []

    async def polish(result: SessionResultOut) -> SessionResultOut:
        seen.append(result.sessionId)
        return result.model_copy(update={"feedback": result.feedback.model_copy(update={"headline": "다듬은 문장"})})

    service = RunbodyService(InMemoryResultRepository(), polisher=polish)
    await service.accept(second_run)
    await service.wait(second_run.sessionId)

    record = await service.get(second_run.sessionId)
    assert record is not None
    assert record.status == "done"
    assert record.result is not None
    assert record.result.feedback.headline == "다듬은 문장"
    assert seen == [second_run.sessionId]


async def test_polisher_failure_keeps_template_result(second_run: SessionIn) -> None:
    """다듬기가 예외를 내도 분석은 실패가 아니다. 템플릿 결과로 done."""

    async def broken(result: SessionResultOut) -> SessionResultOut:
        raise RuntimeError("writer 버그")

    service = RunbodyService(InMemoryResultRepository(), polisher=broken)
    await service.accept(second_run)
    await service.wait(second_run.sessionId)

    record = await service.get(second_run.sessionId)
    assert record is not None
    assert record.status == "done"
    assert record.result is not None
    assert record.result.feedback.source == "template"
