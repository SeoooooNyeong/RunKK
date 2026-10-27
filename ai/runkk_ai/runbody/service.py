"""분석 요청 수락 → 백그라운드 계산 → 저장(F1).

api는 업로드 직후 POST /analyze로 맡기고 바로 202를 받는다. 계산은 별도 스레드에서 돌고(pandas가 CPU를 쓴다)
api는 GET /status로 완료를 확인한 뒤 결과를 가져간다.
같은 세션을 다시 맡기면 다시 계산하지 않는다(실패한 세션만 다시 계산).
같은 사용자의 러닝은 접수 순서대로 하나씩 계산한다(이전 기록 읽기 → 계산 → 저장, S15P31A207-216).
그래야 거의 동시에 올라온 두 러닝 중 두 번째가 첫 번째 결과를 지난 러닝·회복 추세·최근 부하에 넣는다.
나중 러닝이 먼저 접수되면(업로드 순서가 거꾸로면) 앞 러닝은 그 결과에 빠진다. 앱은 러닝 직후 올리므로 드문 경우다.
로그에는 세션 ID·결과·지연·예외 클래스만 남기고 요청 본문(심박·위치)은 남기지 않는다.
"""

from __future__ import annotations

import asyncio
import logging
import time
from collections import Counter
from collections.abc import AsyncIterator, Awaitable, Callable, Sequence
from contextlib import asynccontextmanager
from datetime import UTC, datetime, timedelta

from runkk_ai.runbody.engine import analyze_session
from runkk_ai.runbody.load import CHRONIC_DAYS, engine_load
from runkk_ai.runbody.repository import ResultRepository, SessionRecord, with_status
from runkk_ai.runbody.schemas import Metrics, SessionIn, SessionResultOut

logger = logging.getLogger(__name__)

Analyzer = Callable[[SessionIn, Sequence[SessionResultOut], Metrics | None], Awaitable[SessionResultOut]]

HISTORY_LIMIT = 3  # 회복 추세는 직전 3회, 지난 러닝 비교는 직전 1회를 본다
MAX_CONCURRENT_ANALYSES = 2  # 계산 스레드 수. 같은 컨테이너의 /comments·/faces 응답을 막지 않게 작게 둔다
ENGINE_ERROR = "ENGINE_ERROR"


async def analyze_in_thread(
    session: SessionIn, history: Sequence[SessionResultOut], load: Metrics | None
) -> SessionResultOut:
    return await asyncio.to_thread(analyze_session, session, history, load)


def _now() -> datetime:
    return datetime.now(UTC)


class RunbodyService:
    def __init__(
        self,
        repository: ResultRepository,
        *,
        analyzer: Analyzer = analyze_in_thread,
        clock: Callable[[], datetime] = _now,
    ) -> None:
        self._repository = repository
        self._analyzer = analyzer
        self._clock = clock
        self._semaphore = asyncio.Semaphore(MAX_CONCURRENT_ANALYSES)
        self._tasks: dict[str, asyncio.Task[None]] = {}
        self._user_locks: dict[str, asyncio.Lock] = {}
        self._user_waiting: Counter[str] = Counter()

    async def accept(self, session: SessionIn) -> SessionRecord | None:
        """수락한 기록(새로 만든 pending 또는 기존 기록). 같은 세션 ID가 다른 사용자 것이면 None."""
        pending = SessionRecord(
            session_id=session.sessionId,
            user_id=session.userId,
            started_at=session.startedAt,
            status="pending",
            updated_at=self._clock(),
        )
        record = await self._repository.claim(pending)
        if record.user_id != session.userId:
            logger.info("runbody_accept session_id=%s result=conflict", session.sessionId)
            return None
        if record is pending:
            self._tasks[session.sessionId] = asyncio.create_task(self._run(session, pending))
            logger.info("runbody_accept session_id=%s result=accepted", session.sessionId)
        else:
            logger.info("runbody_accept session_id=%s result=duplicate status=%s", session.sessionId, record.status)
        return record

    async def get(self, session_id: str) -> SessionRecord | None:
        return await self._repository.get(session_id)

    async def wait(self, session_id: str) -> None:
        """진행 중인 계산이 끝날 때까지 기다린다(시험·종료 정리용)."""
        task = self._tasks.get(session_id)
        if task is not None:
            await asyncio.wait({task})

    async def aclose(self) -> None:
        tasks = list(self._tasks.values())
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.wait(tasks)

    async def _run(self, session: SessionIn, pending: SessionRecord) -> None:
        started = time.perf_counter()
        try:
            async with self._user_turn(session.userId):
                async with self._semaphore:
                    history = await self._repository.history(session.userId, session.startedAt, HISTORY_LIMIT)
                    load = await self._recent_load(session)
                    result = await self._analyzer(session, history, load)
                await self._repository.save(with_status(pending, "done", self._clock(), result=result))
            self._log(session.sessionId, "done", started, len(history))
        except asyncio.CancelledError:
            # 종료 중 취소. pending으로 두면 api가 영원히 기다리므로 실패로 남겨 다시 맡길 수 있게 한다
            await self._repository.save(with_status(pending, "failed", self._clock(), error_code="CANCELLED"))
            self._log(session.sessionId, "cancelled", started, 0)
            raise
        except Exception as exc:
            await self._repository.save(with_status(pending, "failed", self._clock(), error_code=ENGINE_ERROR))
            self._log(session.sessionId, "failed", started, 0, detail=type(exc).__name__)
        finally:
            self._tasks.pop(session.sessionId, None)

    @asynccontextmanager
    async def _user_turn(self, user_id: str) -> AsyncIterator[None]:
        """같은 사용자는 한 번에 하나씩(asyncio.Lock은 기다린 순서대로 넘겨준다). 기다리는 쪽이 없으면 잠금을 치운다."""
        lock = self._user_locks.setdefault(user_id, asyncio.Lock())
        self._user_waiting[user_id] += 1
        try:
            async with lock:
                yield
        finally:
            self._user_waiting[user_id] -= 1
            if self._user_waiting[user_id] == 0:
                del self._user_waiting[user_id]
                self._user_locks.pop(user_id, None)

    async def _recent_load(self, session: SessionIn) -> Metrics:
        """이 러닝 직전 28일 기록 + 이 러닝 거리로 최근 부하를 만든다(acwr_high 판단용)."""
        previous = await self._repository.results_between(
            session.userId, session.startedAt - timedelta(days=CHRONIC_DAYS), session.startedAt
        )
        first = await self._repository.first_started_at(session.userId)
        return engine_load(previous, first, round(session.distanceMeters), session.startedAt)

    @staticmethod
    def _log(session_id: str, result: str, started: float, history: int, detail: str = "") -> None:
        logger.info(
            "runbody_analysis session_id=%s result=%s latency_ms=%d history=%d detail=%s",
            session_id,
            result,
            int((time.perf_counter() - started) * 1000),
            history,
            detail,
        )
