"""POST /coach/chat (F5, S15P31A207-193). 200 text/event-stream / 404·422·429·503 {"reason"}.

api가 앱의 질문을 받아 사용자를 인증한 뒤 userId를 넣어 부른다. 응답은 SSE(서버 보낸 이벤트)로 흘려보낸다.
이벤트 순서: meta → (tool …) → delta … → done, 또는 중간 실패면 error. 형식은 service.py 맨 위.
"""

from __future__ import annotations

import json
from collections.abc import AsyncIterator
from typing import Annotated

from fastapi import APIRouter, Depends, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import ValidationError

from runkk_ai.coach.schemas import ChatIn
from runkk_ai.coach.service import ChatRejected, CoachService

CHAT_BODY_MAX_BYTES = 8 * 1024  # 질문 1,000자 + userId·conversationId. 넉넉히 잡되 큰 본문은 읽지 않는다
_SSE_HEADERS = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}  # nginx가 모아 보내지 않게

CoachRouter = APIRouter(prefix="/coach", tags=["coach"])


def get_coach_service(request: Request) -> CoachService:
    service: CoachService = request.app.state.coach_service
    return service


def _failure(status_code: int, reason: str) -> JSONResponse:
    return JSONResponse(status_code=status_code, content={"reason": reason})


@CoachRouter.post(
    "/chat",
    response_class=StreamingResponse,
    response_model=None,
    responses={
        status.HTTP_200_OK: {"content": {"text/event-stream": {}}, "description": "meta·tool·delta·done·error 이벤트"},
        status.HTTP_404_NOT_FOUND: {"description": "CONVERSATION_NOT_FOUND"},
        status.HTTP_413_CONTENT_TOO_LARGE: {"description": "TOO_LARGE"},
        status.HTTP_429_TOO_MANY_REQUESTS: {"description": "QUOTA_EXCEEDED"},
        status.HTTP_503_SERVICE_UNAVAILABLE: {"description": "LLM_UNAVAILABLE"},
    },
    openapi_extra={
        "requestBody": {"required": True, "content": {"application/json": {"schema": ChatIn.model_json_schema()}}}
    },
)
async def chat(
    request: Request, service: Annotated[CoachService, Depends(get_coach_service)]
) -> StreamingResponse | JSONResponse:
    raw = b""
    async for chunk in request.stream():
        raw += chunk
        if len(raw) > CHAT_BODY_MAX_BYTES:
            return _failure(status.HTTP_413_CONTENT_TOO_LARGE, "TOO_LARGE")
    try:
        body = ChatIn.model_validate(json.loads(raw or b"null"))
    except (ValueError, ValidationError) as exc:
        raise RequestValidationError([{"type": "invalid", "loc": ("body",), "msg": "invalid"}]) from exc
    try:
        _, events = await service.start(body)
    except ChatRejected as rejected:
        return _failure(rejected.status, rejected.reason)

    async def body_stream() -> AsyncIterator[str]:
        async for event in events:
            yield event.sse()

    return StreamingResponse(body_stream(), media_type="text/event-stream", headers=_SSE_HEADERS)
