"""월간 RUN바디 조회(F2). 요청 때마다 저장소의 1회 결과로 계산한다(집계는 수 ms라 저장하지 않는다).

analytics 스키마가 생기면(S15P31A207-163) 월 결과를 저장해 두고 api가 뷰로 읽게 바꾼다.
"""

from __future__ import annotations

import logging
from collections.abc import Callable
from datetime import UTC, datetime, timedelta

from runkk_ai.runbody.load import CHRONIC_DAYS
from runkk_ai.runbody.monthly import aggregate_month, month_range
from runkk_ai.runbody.repository import ResultRepository
from runkk_ai.runbody.schemas import MonthlyOut

logger = logging.getLogger(__name__)

_JUST_AFTER = timedelta(microseconds=1)


def _now() -> datetime:
    return datetime.now(UTC)


class MonthlyService:
    def __init__(self, repository: ResultRepository, *, clock: Callable[[], datetime] = _now) -> None:
        self._repository = repository
        self._clock = clock

    async def month(self, user_id: str, year_month: str) -> MonthlyOut:
        """이번 달이면 지금까지, 지난달이면 그 달 마지막 순간까지. 아직 오지 않은 달은 러닝 0회로 낸다."""
        start, end = month_range(year_month)
        as_of = min(self._clock(), end - _JUST_AFTER)
        window_start = min(start, as_of - timedelta(days=CHRONIC_DAYS))
        results = await self._repository.results_between(user_id, window_start, as_of + _JUST_AFTER)
        first = await self._repository.first_started_at(user_id)
        month = aggregate_month(user_id, year_month, results, as_of, first)
        logger.info("runbody_month year_month=%s sessions=%d", year_month, month.sessionCount)
        return month
