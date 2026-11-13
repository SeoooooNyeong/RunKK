"""POST /coach/chat 요청·응답 형식(S15P31A207-193)과 지난 문답 조회 응답(S15P31A207-300)."""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

ID_PATTERN = r"^[A-Za-z0-9_.:-]{1,64}$"
MESSAGE_MAX_CHARS = 1000


class ChatIn(BaseModel):
    """userId는 api가 인증한 사용자다. 코치 도구는 이 사용자 기록만 읽는다.

    conversationId를 비우면 새 대화를 연다(첫 meta 이벤트로 새 ID를 알려 준다).
    """

    model_config = ConfigDict(extra="forbid")

    userId: str = Field(pattern=ID_PATTERN)
    conversationId: str | None = Field(default=None, pattern=ID_PATTERN)
    message: str = Field(min_length=1, max_length=MESSAGE_MAX_CHARS)


class MessageOut(BaseModel):
    """지난 문답 한 건. role은 user(사용자 질문)·assistant(코치 답), at은 남긴 시각(UTC)."""

    role: Literal["user", "assistant"]
    content: str
    at: datetime


class MessagesOut(BaseModel):
    """GET /coach/users/{userId}/conversations/{conversationId}/messages 응답. 오래된 것부터 담는다."""

    conversationId: str
    messages: list[MessageOut]
