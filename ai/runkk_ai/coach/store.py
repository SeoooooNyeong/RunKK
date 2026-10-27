"""코치 대화 기록 저장소(coach_message). 서비스는 CoachMessageStore 규약만 안다.

메모리 구현은 재시작하면 빈다. PostgreSQL 구현은 runkk_ai.storage.coach_store(analytics.coach_message).
사용자 질문과 코치의 최종 답만 남긴다(도구 호출·결과는 남기지 않는다. 다시 물으면 다시 꺼낸다).
"""

from __future__ import annotations

import asyncio
from collections import OrderedDict
from dataclasses import dataclass
from datetime import datetime
from typing import Literal, Protocol

Role = Literal["user", "assistant"]
MEMORY_MAX_CONVERSATIONS = 2_000


@dataclass(frozen=True, slots=True)
class CoachMessage:
    conversation_id: str
    user_id: str
    role: Role
    content: str
    at: datetime
    model: str | None = None
    input_tokens: int | None = None
    output_tokens: int | None = None
    tools: str | None = None  # 이 답을 만들며 부른 도구 이름(쉼표로 구분)


class CoachMessageStore(Protocol):
    async def owner(self, conversation_id: str) -> str | None:
        """대화를 연 사용자. 없는 대화면 None."""
        ...

    async def recent(self, conversation_id: str, limit: int) -> list[CoachMessage]:
        """최근 limit개를 오래된 순으로."""
        ...

    async def append(self, message: CoachMessage) -> None: ...


class InMemoryCoachStore:
    def __init__(self, max_conversations: int = MEMORY_MAX_CONVERSATIONS) -> None:
        self._conversations: OrderedDict[str, list[CoachMessage]] = OrderedDict()
        self._max = max_conversations
        self._lock = asyncio.Lock()

    async def owner(self, conversation_id: str) -> str | None:
        messages = self._conversations.get(conversation_id)
        return messages[0].user_id if messages else None

    async def recent(self, conversation_id: str, limit: int) -> list[CoachMessage]:
        return list(self._conversations.get(conversation_id, []))[-limit:]

    async def append(self, message: CoachMessage) -> None:
        async with self._lock:
            self._conversations.setdefault(message.conversation_id, []).append(message)
            self._conversations.move_to_end(message.conversation_id)
            while len(self._conversations) > self._max:
                self._conversations.popitem(last=False)
