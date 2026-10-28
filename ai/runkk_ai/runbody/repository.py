"""분석 상태·결과 저장소. 서비스는 ResultRepository만 안다.

이 MR은 프로세스 메모리 구현만 둔다. analytics 스키마(PostgreSQL) 구현은 S15P31A207-163에서 같은 Protocol로 붙인다.
메모리 구현은 재시작하면 비고, 오래된 기록부터 버린다(상한 MEMORY_MAX_RECORDS).
"""

from __future__ import annotations

import asyncio
from collections import OrderedDict
from dataclasses import dataclass, replace
from datetime import datetime
from typing import Protocol

from runkk_ai.runbody.schemas import AnalysisStatus, SessionResultOut

MEMORY_MAX_RECORDS = 5_000


@dataclass(frozen=True, slots=True)
class SessionRecord:
    session_id: str
    user_id: str
    started_at: datetime
    status: AnalysisStatus
    updated_at: datetime
    result: SessionResultOut | None = None
    error_code: str | None = None


class ResultRepository(Protocol):
    async def claim(self, record: SessionRecord) -> SessionRecord:
        """같은 session_id가 없거나 실패 상태면 record(pending)를 저장해 돌려준다. 있으면 기존 기록을 그대로 돌려준다.

        확인과 저장이 한 번에 일어나야 같은 세션을 두 번 계산하지 않는다.
        """
        ...

    async def save(self, record: SessionRecord) -> None: ...

    async def get(self, session_id: str) -> SessionRecord | None: ...

    async def history(self, user_id: str, before: datetime, limit: int) -> list[SessionResultOut]:
        """user_id의 완료된 결과 중 before보다 먼저 시작한 것을 오래된 순으로 최대 limit개."""
        ...

    async def results_between(self, user_id: str, start: datetime, end: datetime) -> list[SessionResultOut]:
        """user_id의 완료된 결과 중 start <= 시작 시각 < end인 것을 오래된 순으로(월간·부하 계산용)."""
        ...

    async def first_started_at(self, user_id: str) -> datetime | None:
        """user_id의 완료된 러닝 중 가장 이른 시작 시각(ACWR을 켜는 날짜 계산용)."""
        ...


class InMemoryResultRepository:
    def __init__(self, max_records: int = MEMORY_MAX_RECORDS) -> None:
        self._records: OrderedDict[str, SessionRecord] = OrderedDict()
        self._max_records = max_records
        self._lock = asyncio.Lock()

    async def claim(self, record: SessionRecord) -> SessionRecord:
        async with self._lock:
            existing = self._records.get(record.session_id)
            if existing is not None and existing.status != "failed":
                return existing
            self._put(record)
            return record

    async def save(self, record: SessionRecord) -> None:
        async with self._lock:
            self._put(record)

    async def get(self, session_id: str) -> SessionRecord | None:
        return self._records.get(session_id)

    async def history(self, user_id: str, before: datetime, limit: int) -> list[SessionResultOut]:
        done = sorted(
            (
                r
                for r in self._records.values()
                if r.user_id == user_id and r.status == "done" and r.started_at < before
            ),
            key=lambda r: r.started_at,
        )
        return [r.result for r in done[-limit:] if r.result is not None]

    async def results_between(self, user_id: str, start: datetime, end: datetime) -> list[SessionResultOut]:
        done = sorted(
            (r for r in self._done(user_id) if start <= r.started_at < end),
            key=lambda r: r.started_at,
        )
        return [r.result for r in done if r.result is not None]

    async def first_started_at(self, user_id: str) -> datetime | None:
        return min((r.started_at for r in self._done(user_id)), default=None)

    def _done(self, user_id: str) -> list[SessionRecord]:
        return [r for r in self._records.values() if r.user_id == user_id and r.status == "done"]

    def _put(self, record: SessionRecord) -> None:
        self._records[record.session_id] = record
        self._records.move_to_end(record.session_id)
        while len(self._records) > self._max_records:
            self._records.popitem(last=False)


def with_status(
    record: SessionRecord,
    status: AnalysisStatus,
    at: datetime,
    *,
    result: SessionResultOut | None = None,
    error_code: str | None = None,
) -> SessionRecord:
    return replace(record, status=status, updated_at=at, result=result, error_code=error_code)
