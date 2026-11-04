"""코치 대화 본문 보관 기한(S15P31A207-258, 처리방침 D-436: 대화 본문은 90일 뒤 지운다).

기동 때 한 번, 그 뒤 interval_s마다 cutoff(지금 - days)보다 오래된 메시지를 지운다. 실패해도 서버는 계속 돌고
다음 주기에 다시 지운다(로그에는 예외 종류만, 문구는 DB 주소가 섞일 수 있어 남기지 않는다).
기한은 처리방침에 묶인 값이라 환경 변수로 열지 않는다.
"""

from __future__ import annotations

import asyncio
import contextlib
import logging
from collections.abc import Callable
from datetime import UTC, datetime, timedelta

from runkk_ai.coach.store import CoachMessageStore

logger = logging.getLogger(__name__)

COACH_RETENTION_DAYS = 90
SWEEP_INTERVAL_S = 6 * 60 * 60


def _utcnow() -> datetime:
    return datetime.now(UTC)


class CoachRetention:
    def __init__(
        self,
        store: CoachMessageStore,
        *,
        days: int = COACH_RETENTION_DAYS,
        interval_s: float = SWEEP_INTERVAL_S,
        clock: Callable[[], datetime] = _utcnow,
    ) -> None:
        if days <= 0:
            raise ValueError("보관 일수는 1 이상이어야 한다")
        self._store = store
        self._days = days
        self._interval_s = interval_s
        self._clock = clock
        self._task: asyncio.Task[None] | None = None

    @property
    def days(self) -> int:
        return self._days

    async def sweep(self) -> int:
        cutoff = self._clock() - timedelta(days=self._days)
        try:
            deleted = await self._store.purge_before(cutoff)
        except Exception as exc:
            logger.warning("coach_retention result=error detail=%s", type(exc).__name__)
            return 0
        logger.info("coach_retention result=ok deleted=%d days=%d", deleted, self._days)
        return deleted

    def start(self) -> None:
        if self._task is None:
            self._task = asyncio.create_task(self._run(), name="coach-retention")

    async def stop(self) -> None:
        if self._task is not None:
            self._task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._task

    async def _run(self) -> None:
        while True:
            await self.sweep()
            await asyncio.sleep(self._interval_s)
