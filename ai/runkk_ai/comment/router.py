"""POST /comments(6.1.16, D-58). 200 CommentOut / 503 LLM_UNAVAILABLE / 504 LLM_TIMEOUT / 422."""

from __future__ import annotations

import asyncio
import logging
from typing import Annotated

from fastapi import APIRouter, Depends, Request, status
from fastapi.responses import JSONResponse

from runkk_ai.comment.schemas import CommentOut, SummaryIn
from runkk_ai.comment.service import CommentService

logger = logging.getLogger(__name__)

# 라우터 전체 제한의 유일한 원본. api AiServerClient 읽기 제한(3초)보다 짧아야 하고(D-58, H-22),
# Settings.llm_timeout_s는 이보다 짧아야 한다(main.create_app이 확인)
COMMENT_TIMEOUT_S = 2.7

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
        status.HTTP_503_SERVICE_UNAVAILABLE: {"description": "LLM_UNAVAILABLE"},
        status.HTTP_504_GATEWAY_TIMEOUT: {"description": "LLM_TIMEOUT"},
    },
)
async def create(
    body: SummaryIn,
    service: Annotated[CommentService, Depends(get_comment_service)],
) -> CommentOut | JSONResponse:
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
