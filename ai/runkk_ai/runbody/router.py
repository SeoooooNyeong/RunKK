"""RUN바디 1회 분석 엔드포인트(F1). 실패 본문은 모두 {"reason"} 한 필드다.

POST /sessions/{sessionId}/analyze   202 AnalyzeAccepted / 409 SESSION_CONFLICT / 413 TOO_LARGE / 422 INVALID_REQUEST
GET  /sessions/{sessionId}/status    200 StatusOut / 404 SESSION_NOT_FOUND
GET  /sessions/{sessionId}           200 SessionResultOut / 409 ANALYSIS_PENDING·ANALYSIS_FAILED / 404 SESSION_NOT_FOUND

본문은 /comments와 같은 방식으로 상한까지만 메모리에 읽고 넘으면 413이다.
1초 샘플 1시간 러닝이 수백 KB라 상한을 따로 둔다.
"""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, Path, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from runkk_ai.runbody.schemas import AnalyzeAccepted, SessionIn, SessionResultOut, StatusOut
from runkk_ai.runbody.service import RunbodyService

ANALYSIS_BODY_MAX_BYTES = 8 * 1024 * 1024
_JSON_MEDIA_TYPE = "application/json"

RunbodyRouter = APIRouter(prefix="/sessions", tags=["runbody"])

SessionId = Annotated[str, Path(pattern=r"^[A-Za-z0-9_\-:.]{1,64}$")]


def get_runbody_service(request: Request) -> RunbodyService:
    service: RunbodyService = request.app.state.runbody_service
    return service


def _failure(status_code: int, reason: str) -> JSONResponse:
    return JSONResponse(status_code=status_code, content={"reason": reason})


@RunbodyRouter.post(
    "/{session_id}/analyze",
    status_code=status.HTTP_202_ACCEPTED,
    response_model=AnalyzeAccepted,
    responses={
        status.HTTP_409_CONFLICT: {"description": "SESSION_CONFLICT"},
        status.HTTP_413_CONTENT_TOO_LARGE: {"description": "TOO_LARGE"},
    },
    openapi_extra={
        "requestBody": {"required": True, "content": {_JSON_MEDIA_TYPE: {"schema": SessionIn.model_json_schema()}}}
    },
)
async def analyze(
    session_id: SessionId,
    request: Request,
    service: Annotated[RunbodyService, Depends(get_runbody_service)],
) -> AnalyzeAccepted | JSONResponse:
    if _declared_length(request) > ANALYSIS_BODY_MAX_BYTES:
        return _failure(status.HTTP_413_CONTENT_TOO_LARGE, "TOO_LARGE")
    raw = await _read_body(request, ANALYSIS_BODY_MAX_BYTES)
    if raw is None:
        return _failure(status.HTTP_413_CONTENT_TOO_LARGE, "TOO_LARGE")
    session = _parse_session(request, raw)
    if session.sessionId != session_id:
        raise RequestValidationError(
            [{"type": "path_mismatch", "loc": ("path", "session_id"), "msg": "경로와 본문 불일치"}]
        )
    record = await service.accept(session)
    if record is None:
        return _failure(status.HTTP_409_CONFLICT, "SESSION_CONFLICT")
    return AnalyzeAccepted(sessionId=record.session_id, status=record.status)


@RunbodyRouter.get(
    "/{session_id}/status",
    response_model=StatusOut,
    responses={status.HTTP_404_NOT_FOUND: {"description": "SESSION_NOT_FOUND"}},
)
async def get_status(
    session_id: SessionId,
    service: Annotated[RunbodyService, Depends(get_runbody_service)],
) -> StatusOut | JSONResponse:
    record = await service.get(session_id)
    if record is None:
        return _failure(status.HTTP_404_NOT_FOUND, "SESSION_NOT_FOUND")
    return StatusOut(
        sessionId=record.session_id,
        status=record.status,
        updatedAt=record.updated_at,
        errorCode=record.error_code,
    )


@RunbodyRouter.get(
    "/{session_id}",
    response_model=SessionResultOut,
    responses={
        status.HTTP_404_NOT_FOUND: {"description": "SESSION_NOT_FOUND"},
        status.HTTP_409_CONFLICT: {"description": "ANALYSIS_PENDING · ANALYSIS_FAILED"},
    },
)
async def get_result(
    session_id: SessionId,
    service: Annotated[RunbodyService, Depends(get_runbody_service)],
) -> SessionResultOut | JSONResponse:
    record = await service.get(session_id)
    if record is None:
        return _failure(status.HTTP_404_NOT_FOUND, "SESSION_NOT_FOUND")
    if record.status == "done" and record.result is not None:
        return record.result
    reason = "ANALYSIS_FAILED" if record.status == "failed" else "ANALYSIS_PENDING"
    return _failure(status.HTTP_409_CONFLICT, reason)


def _declared_length(request: Request) -> int:
    try:
        return int(request.headers.get("content-length", "0"))
    except ValueError:
        return 0


async def _read_body(request: Request, limit: int) -> bytes | None:
    """Content-Length가 없거나(chunked) 틀려도 상한을 넘는 순간 읽기를 멈춘다(comment.router와 같은 방식)."""
    buffer = bytearray()
    async for chunk in request.stream():
        buffer += chunk
        if len(buffer) > limit:
            return None
    return bytes(buffer)


def _parse_session(request: Request, raw: bytes) -> SessionIn:
    """JSON이 아니거나 SessionIn 정의 밖이면 RequestValidationError(main이 422 INVALID_REQUEST, 입력값 미반환)."""
    media_type = request.headers.get("content-type", "").split(";", 1)[0].strip().lower()
    if media_type and media_type != _JSON_MEDIA_TYPE and not media_type.endswith("+json"):
        raise RequestValidationError([{"type": "content_type", "loc": ("body",), "msg": "JSON 본문이 아니다"}])
    try:
        return SessionIn.model_validate_json(raw)
    except ValidationError as e:
        raise RequestValidationError(e.errors(include_input=False)) from None
