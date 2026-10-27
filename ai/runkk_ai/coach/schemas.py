"""POST /coach/chat 요청·응답 형식(S15P31A207-193)."""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field

_ID_PATTERN = r"^[A-Za-z0-9_.:-]{1,64}$"
MESSAGE_MAX_CHARS = 1000


class ChatIn(BaseModel):
    """userId는 api가 인증한 사용자다. 코치 도구는 이 사용자 기록만 읽는다.

    conversationId를 비우면 새 대화를 연다(첫 meta 이벤트로 새 ID를 알려 준다).
    """

    model_config = ConfigDict(extra="forbid")

    userId: str = Field(pattern=_ID_PATTERN)
    conversationId: str | None = Field(default=None, pattern=_ID_PATTERN)
    message: str = Field(min_length=1, max_length=MESSAGE_MAX_CHARS)
