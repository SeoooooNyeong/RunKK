"""analytics.llm_model 등록부(S15P31A207-197). writer.registry.ModelRegistry 규약을 그대로 따른다."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

from sqlalchemy import Row, select, update
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncConnection, AsyncEngine

from runkk_ai.storage.tables import llm_model
from runkk_ai.writer.registry import ModelNotFound, NoPreviousModel, WriterModel, previous_id

_m = llm_model.c


def _to_model(row: Row[Any]) -> WriterModel:
    return WriterModel(
        model_id=row.model_id,
        base_url=row.base_url,
        note=row.note,
        registered_at=row.registered_at,
        activated_at=row.activated_at,
        active=row.active,
    )


class PgModelRegistry:
    def __init__(self, engine: AsyncEngine) -> None:
        self._engine = engine

    async def models(self) -> list[WriterModel]:
        query = select(llm_model).order_by(_m.registered_at, _m.model_id)
        async with self._engine.connect() as conn:
            return [_to_model(r) for r in (await conn.execute(query)).all()]

    async def active(self) -> WriterModel | None:
        async with self._engine.connect() as conn:
            row = (await conn.execute(select(llm_model).where(_m.active))).first()
        return _to_model(row) if row is not None else None

    async def register(self, model_id: str, base_url: str, note: str | None = None) -> WriterModel:
        """첫 모델만 바로 켠다. 같은 model_id면 주소·메모만 고치고 켜진 모델은 그대로 둔다."""
        async with self._engine.begin() as conn:
            empty = (await conn.execute(select(_m.model_id).limit(1))).first() is None
            now = datetime.now(UTC)
            upsert = insert(llm_model).values(
                model_id=model_id,
                base_url=base_url,
                note=note,
                registered_at=now,
                activated_at=now if empty else None,
                active=empty,
            )
            statement = upsert.on_conflict_do_update(
                index_elements=[_m.model_id], set_={"base_url": base_url, "note": note}
            ).returning(llm_model)
            return _to_model((await conn.execute(statement)).one())

    async def activate(self, model_id: str) -> WriterModel:
        async with self._engine.begin() as conn:
            return await self._activate(conn, model_id)

    async def rollback(self) -> WriterModel:
        async with self._engine.begin() as conn:
            rows = (await conn.execute(select(llm_model))).all()
            target = previous_id([_to_model(r) for r in rows])
            if target is None:
                raise NoPreviousModel
            return await self._activate(conn, target)

    @staticmethod
    async def _activate(conn: AsyncConnection, model_id: str) -> WriterModel:
        """먼저 다 끄고 하나만 켠다(부분 유일 인덱스에 걸리지 않게 순서를 지킨다)."""
        if (await conn.execute(select(_m.model_id).where(_m.model_id == model_id))).first() is None:
            raise ModelNotFound(model_id)
        await conn.execute(update(llm_model).where(_m.active, _m.model_id != model_id).values(active=False))
        statement = (
            update(llm_model)
            .where(_m.model_id == model_id)
            .values(active=True, activated_at=datetime.now(UTC))
            .returning(llm_model)
        )
        return _to_model((await conn.execute(statement)).one())
