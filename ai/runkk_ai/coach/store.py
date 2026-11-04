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

    async def purge_user(self, user_id: str) -> int:
        """user_id의 대화(모든 메시지)를 지우고 지운 메시지 수를 돌려준다(탈퇴 파기, S15P31A207-219)."""
        ...

    async def purge_before(self, cutoff: datetime) -> int:
        """cutoff보다 먼저 남긴 메시지를 지우고 지운 수를 돌려준다(보관 기한, S15P31A207-258)."""
        ...


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

    async def purge_user(self, user_id: str) -> int:
        async with self._lock:
            mine = [cid for cid, messages in self._conversations.items() if messages and messages[0].user_id == user_id]
            count = sum(len(self._conversations.pop(cid)) for cid in mine)
        return count

    async def purge_before(self, cutoff: datetime) -> int:
        count = 0
        async with self._lock:
            for cid in list(self._conversations):
                kept = [m for m in self._conversations[cid] if m.at >= cutoff]
                count += len(self._conversations[cid]) - len(kept)
                if kept:
                    self._conversations[cid] = kept
                else:
                    del self._conversations[cid]
        return count
