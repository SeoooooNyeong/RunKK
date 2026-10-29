"""analytics.coach_message 저장소(S15P31A207-193). coach.store.CoachMessageStore 규약을 그대로 따른다."""

from __future__ import annotations

from typing import Any

from sqlalchemy import Row, delete, select
from sqlalchemy.ext.asyncio import AsyncEngine

from runkk_ai.coach.store import CoachMessage
from runkk_ai.storage.tables import coach_message

_c = coach_message.c


def _to_message(row: Row[Any]) -> CoachMessage:
    return CoachMessage(
        conversation_id=row.conversation_id,
        user_id=row.user_id,
        role=row.role,
        content=row.content,
        at=row.created_at,
        model=row.model,
        input_tokens=row.input_tokens,
        output_tokens=row.output_tokens,
        tools=row.tools,
    )


class PgCoachStore:
    def __init__(self, engine: AsyncEngine) -> None:
        self._engine = engine

    async def owner(self, conversation_id: str) -> str | None:
        query = select(_c.user_id).where(_c.conversation_id == conversation_id).order_by(_c.id).limit(1)
        async with self._engine.connect() as conn:
            owner: str | None = (await conn.execute(query)).scalar_one_or_none()
        return owner

    async def recent(self, conversation_id: str, limit: int) -> list[CoachMessage]:
        query = select(coach_message).where(_c.conversation_id == conversation_id).order_by(_c.id.desc()).limit(limit)
        async with self._engine.connect() as conn:
            rows = (await conn.execute(query)).all()
        return [_to_message(r) for r in reversed(rows)]

    async def append(self, message: CoachMessage) -> None:
        async with self._engine.begin() as conn:
            await conn.execute(
                coach_message.insert().values(
                    conversation_id=message.conversation_id,
                    user_id=message.user_id,
                    role=message.role,
                    content=message.content,
                    model=message.model,
                    input_tokens=message.input_tokens,
                    output_tokens=message.output_tokens,
                    tools=message.tools,
                    created_at=message.at,
                )
            )

    async def purge_user(self, user_id: str) -> int:
        async with self._engine.begin() as conn:
            deleted = (await conn.execute(delete(coach_message).where(_c.user_id == user_id))).rowcount
        return int(deleted)
