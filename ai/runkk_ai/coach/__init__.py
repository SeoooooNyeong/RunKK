"""AI 코치(F5). 도구 3종(S15P31A207-192)과 대화 API POST /coach/chat SSE(S15P31A207-193).

coach는 runbody의 결과(저장소·월간 서비스)를 읽기만 한다. runbody·writer는 coach를 모르고,
storage는 대화 저장소 규약(coach.store)만 구현한다. main이 끼운다.
"""

from runkk_ai.coach.model import ClaudeCoachModel, CoachModel, ModelTurn, TextDelta, ToolCall
from runkk_ai.coach.router import CHAT_BODY_MAX_BYTES, CoachRouter, get_coach_service
from runkk_ai.coach.schemas import ChatIn
from runkk_ai.coach.service import ChatEvent, ChatRejected, CoachService
from runkk_ai.coach.store import CoachMessage, CoachMessageStore, InMemoryCoachStore
from runkk_ai.coach.tools import TOOL_SPECS, CoachTools, monthly_digest, session_digest

__all__ = [
    "CHAT_BODY_MAX_BYTES",
    "TOOL_SPECS",
    "ChatEvent",
    "ChatIn",
    "ChatRejected",
    "ClaudeCoachModel",
    "CoachMessage",
    "CoachMessageStore",
    "CoachModel",
    "CoachRouter",
    "CoachService",
    "CoachTools",
    "InMemoryCoachStore",
    "ModelTurn",
    "TextDelta",
    "ToolCall",
    "get_coach_service",
    "monthly_digest",
    "session_digest",
]
