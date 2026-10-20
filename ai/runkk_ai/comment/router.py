"""POST /comments(6.1.16, D-58). 200 CommentOut / 503 LLM_UNAVAILABLE / 504 LLM_TIMEOUT / 422."""

from __future__ import annotations

import asyncio
from typing import Annotated

from fastapi import APIRouter, Depends, Request, status
from fastapi.responses import JSONResponse

from runkk_ai.comment.schemas import CommentOut, SummaryIn
from runkk_ai.comment.service import CommentService

# 라우터 전체 제한. api AiServerClient 읽기 제한(3초)보다 짧아야 한다(D-58, H-22)
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
        return _failure(status.HTTP_504_GATEWAY_TIMEOUT, "LLM_TIMEOUT")
    if result.outcome == "ok" and result.text is not None:
        return CommentOut(text=result.text)
    if result.outcome == "timeout":
        return _failure(status.HTTP_504_GATEWAY_TIMEOUT, "LLM_TIMEOUT")
    return _failure(status.HTTP_503_SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE")
