"""analytics 스키마 저장소(S15P31A207-174). runbody.repository.ResultRepository 규약을 그대로 따른다.

상태가 바뀔 때마다(접수·완료·실패) analysis_event에 한 줄을 같은 트랜잭션으로 남긴다.
결과는 응답 JSON(camelCase) 그대로 jsonb에 넣고, 읽을 때 SessionResultOut으로 다시 검증한다.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

from sqlalchemy import Row, delete, func, select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncConnection, AsyncEngine

from runkk_ai.runbody.repository import SessionRecord
from runkk_ai.runbody.schemas import SessionResultOut
from runkk_ai.storage.tables import analysis_event, session_result

_t = session_result.c


def _values(record: SessionRecord) -> dict[str, Any]:
    result = record.result
    return {
        "session_id": record.session_id,
        "user_id": record.user_id,
        "started_at": record.started_at,
        "status": record.status,
        "error_code": record.error_code,
        "engine_version": result.engineVersion if result is not None else None,
        "result": result.model_dump(mode="json") if result is not None else None,
        "updated_at": record.updated_at,
    }


def _to_record(row: Row[Any]) -> SessionRecord:
    return SessionRecord(
        session_id=row.session_id,
        user_id=row.user_id,
        started_at=row.started_at,
        status=row.status,
        updated_at=row.updated_at,
        result=SessionResultOut.model_validate(row.result) if row.result is not None else None,
        error_code=row.error_code,
    )


class PgResultRepository:
    def __init__(self, engine: AsyncEngine) -> None:
        self._engine = engine

    async def claim(self, record: SessionRecord) -> SessionRecord:
        values = _values(record)
        insert_stmt = insert(session_result).values(values)
        # 실패한 세션만 새 접수로 덮는다. 진행 중·완료면 아무것도 바꾸지 않고 기존 행을 읽는다
        stmt = insert_stmt.on_conflict_do_update(
            index_elements=[_t.session_id],
            set_={k: insert_stmt.excluded[k] for k in values if k != "session_id"},
            where=_t.status == "failed",
        ).returning(_t.session_id)
        async with self._engine.begin() as conn:
            claimed = (await conn.execute(stmt)).first()
            if claimed is not None:
                await self._event(conn, record)
                return record
            row = (await conn.execute(select(session_result).where(_t.session_id == record.session_id))).one()
        return _to_record(row)

    async def save(self, record: SessionRecord) -> None:
        values = _values(record)
        stmt = insert(session_result).values(values)
        stmt = stmt.on_conflict_do_update(
            index_elements=[_t.session_id],
            set_={k: stmt.excluded[k] for k in values if k != "session_id"},
        )
        async with self._engine.begin() as conn:
            await conn.execute(stmt)
            await self._event(conn, record)

    async def get(self, session_id: str) -> SessionRecord | None:
        async with self._engine.connect() as conn:
            row = (await conn.execute(select(session_result).where(_t.session_id == session_id))).first()
        return _to_record(row) if row is not None else None

    async def history(self, user_id: str, before: datetime, limit: int) -> list[SessionResultOut]:
        query = (
            select(_t.result)
            .where(_t.user_id == user_id, _t.status == "done", _t.started_at < before)
            .order_by(_t.started_at.desc())
            .limit(limit)
        )
        rows = await self._results(query)
        return rows[::-1]

    async def results_between(self, user_id: str, start: datetime, end: datetime) -> list[SessionResultOut]:
        query = (
            select(_t.result)
            .where(_t.user_id == user_id, _t.status == "done", _t.started_at >= start, _t.started_at < end)
            .order_by(_t.started_at)
        )
        return await self._results(query)

    async def first_started_at(self, user_id: str) -> datetime | None:
        query = select(func.min(_t.started_at)).where(_t.user_id == user_id, _t.status == "done")
        async with self._engine.connect() as conn:
            first: datetime | None = (await conn.execute(query)).scalar_one()
        return first

    async def purge_user(self, user_id: str) -> int:
        """analysis_event는 FK ON DELETE CASCADE로 함께 지워진다."""
        async with self._engine.begin() as conn:
            deleted = (await conn.execute(delete(session_result).where(_t.user_id == user_id))).rowcount
        return int(deleted)

    async def _results(self, query: Any) -> list[SessionResultOut]:
        async with self._engine.connect() as conn:
            rows = (await conn.execute(query)).scalars().all()
        return [SessionResultOut.model_validate(r) for r in rows]

    @staticmethod
    async def _event(conn: AsyncConnection, record: SessionRecord) -> None:
        await conn.execute(
            analysis_event.insert().values(
                session_id=record.session_id,
                status=record.status,
                error_code=record.error_code,
                at=record.updated_at,
            )
        )
