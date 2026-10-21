"""POST /comments(6.1.16, D-58). 200 CommentOut / 413 TOO_LARGE / 422 / 503 LLM_UNAVAILABLE / 504 LLM_TIMEOUT.

본문은 /faces와 같은 방식으로 상한(COMMENT_BODY_MAX_BYTES)까지만 메모리에 읽고 넘으면 413이다(코드 리뷰 3차 N5).
FastAPI 본문 매개변수는 상한 없이 전부 읽은 뒤 파싱하므로 직접 읽어 SummaryIn으로 검증하고,
실패는 같은 422 처리기(main._invalid_request)로 보낸다.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Annotated

from fastapi import APIRouter, Depends, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from runkk_ai.comment.schemas import CommentOut, SummaryIn
from runkk_ai.comment.service import CommentService

logger = logging.getLogger(__name__)

# 라우터 전체 제한의 유일한 원본. api AiServerClient 읽기 제한(3초)보다 짧아야 하고(D-58, H-22),
# Settings.llm_timeout_s는 이보다 짧아야 한다(main.create_app이 확인)
COMMENT_TIMEOUT_S = 2.7

# 본문 상한. SummaryIn JSON은 200바이트 안팎이라 넉넉히 잡되 메모리에 큰 본문이 올라가지 않게 한다(N5)
COMMENT_BODY_MAX_BYTES = 4 * 1024

_JSON_MEDIA_TYPE = "application/json"

CommentRouter = APIRouter(tags=["comment"])


def get_comment_service(request: Request) -> CommentService:
    service: CommentService = request.app.state.comment_service
    return service


def _failure(status_code: int, reason: str) -> JSONResponse:
    return JSONResponse(status_code=status_code, content={"reason": reason})


@CommentRouter.post(
    "/comments",
    response_model=CommentOut,
    responses={
        status.HTTP_413_CONTENT_TOO_LARGE: {"description": "TOO_LARGE"},
        status.HTTP_503_SERVICE_UNAVAILABLE: {"description": "LLM_UNAVAILABLE"},
        status.HTTP_504_GATEWAY_TIMEOUT: {"description": "LLM_TIMEOUT"},
    },
    openapi_extra={
        "requestBody": {
            "required": True,
            "content": {_JSON_MEDIA_TYPE: {"schema": SummaryIn.model_json_schema()}},
        }
    },
)
async def create(
    request: Request,
    service: Annotated[CommentService, Depends(get_comment_service)],
) -> CommentOut | JSONResponse:
    if _declared_length(request) > COMMENT_BODY_MAX_BYTES:
        return _failure(status.HTTP_413_CONTENT_TOO_LARGE, "TOO_LARGE")
    raw = await _read_body(request, COMMENT_BODY_MAX_BYTES)
    if raw is None:
        return _failure(status.HTTP_413_CONTENT_TOO_LARGE, "TOO_LARGE")
    body = _parse_summary(request, raw)
    try:
        async with asyncio.timeout(COMMENT_TIMEOUT_S):
            result = await service.generate(body)
    except TimeoutError:
        # 서비스는 취소로만 남긴다(연결 끊김과 구분). 라우터 제한에 걸린 시간 초과는 여기서 센다
        logger.info("comment_route result=timeout timeout_s=%.1f", COMMENT_TIMEOUT_S)
        return _failure(status.HTTP_504_GATEWAY_TIMEOUT, "LLM_TIMEOUT")
    if result.outcome == "ok" and result.text is not None:
        return CommentOut(text=result.text)
    if result.outcome == "timeout":
        return _failure(status.HTTP_504_GATEWAY_TIMEOUT, "LLM_TIMEOUT")
    return _failure(status.HTTP_503_SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE")


def _declared_length(request: Request) -> int:
    try:
        return int(request.headers.get("content-length", "0"))
    except ValueError:
        return 0


async def _read_body(request: Request, limit: int) -> bytes | None:
    """Content-Length가 없거나(chunked) 틀려도 상한을 넘는 순간 읽기를 멈춘다(face.router와 같은 방식)."""
    buffer = bytearray()
    async for chunk in request.stream():
        buffer += chunk
        if len(buffer) > limit:
            return None
    return bytes(buffer)


def _parse_summary(request: Request, raw: bytes) -> SummaryIn:
    """JSON이 아니거나 SummaryIn 정의 밖이면 RequestValidationError(main이 422 INVALID_REQUEST, 입력값 미반환)."""
    media_type = request.headers.get("content-type", "").split(";", 1)[0].strip().lower()
    if media_type and media_type != _JSON_MEDIA_TYPE and not media_type.endswith("+json"):
        raise RequestValidationError([{"type": "content_type", "loc": ("body",), "msg": "JSON 본문이 아니다"}])
    try:
        return SummaryIn.model_validate_json(raw)
    except ValidationError as e:
        raise RequestValidationError(e.errors(include_input=False)) from None
