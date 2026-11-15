"""아바타 중계 엔드포인트(Node 명세 v1.2를 api용 경로로 옮긴 것).

POST /users/{userId}/faces/jobs[?attempt=n] 셀카(application/octet-stream) → 202 HeadJobOut (attempt 1~20, 기본 1)
GET  /users/{userId}/faces/jobs/{jobId}         → 200 HeadJobOut / 404 JOB_NOT_FOUND
GET  /users/{userId}/faces/jobs/{jobId}/result  → 200 HeadOut / 404 JOB_NOT_FOUND / 409 JOB_NOT_READY
GET  /users/{userId}/faces/latest               → 200 HeadOut / 404 HEAD_NOT_FOUND
GET  /avatar/bodies/{bodyId}                    → 200 BodyOut / 404 BODY_NOT_FOUND

셀카는 UploadFile을 쓰지 않고 메모리에서만 읽는다(1MB가 넘으면 임시 파일로 넘어간다). 입력 크기 초과 413,
형식 오류 415, 머리 정보가 깨졌으면 422 UNSUPPORTED_IMAGE, 픽셀 수가 상한을 넘으면 422 TOO_LARGE(L28),
아바타 서버 실패 503 / 시간 초과 504 + reason.
"""

from __future__ import annotations

import asyncio
from typing import Annotated, Any
from uuid import UUID

from fastapi import APIRouter, Depends, Path, Query, Request, status
from fastapi.responses import JSONResponse
from starlette.requests import ClientDisconnect

from runkk_ai.face.client import AvatarError
from runkk_ai.face.schemas import (
    JPEG_SIGNATURE,
    PNG_SIGNATURE,
    BodyOut,
    FaceFailureReason,
    HeadJobOut,
    HeadOut,
    image_dimensions,
)
from runkk_ai.face.service import AvatarService

FaceRouter = APIRouter(tags=["face"])

_CLIENT_CLOSED_REQUEST = 499

_STATUS_BY_REASON: dict[FaceFailureReason, int] = {
    "UNSUPPORTED_IMAGE": status.HTTP_422_UNPROCESSABLE_CONTENT,
    "TOO_LARGE": status.HTTP_422_UNPROCESSABLE_CONTENT,
    "UPSTREAM_FAILED": status.HTTP_503_SERVICE_UNAVAILABLE,
    "TIMEOUT": status.HTTP_504_GATEWAY_TIMEOUT,
    "JOB_NOT_FOUND": status.HTTP_404_NOT_FOUND,
    "HEAD_NOT_FOUND": status.HTTP_404_NOT_FOUND,
    "BODY_NOT_FOUND": status.HTTP_404_NOT_FOUND,
    "JOB_NOT_READY": status.HTTP_409_CONFLICT,
}

# Node userId 허용 문자(명세 2장). 벗어나면 Node를 부르지 않고 422 INVALID_REQUEST
UserId = Annotated[str, Path(pattern=r"^[A-Za-z0-9_-]{1,128}$")]
BodyId = Annotated[str, Path(pattern=r"^[A-Za-z0-9_-]{1,64}$")]
# 명시적 재시도 번호(DV-9). 1이면 예전과 같은 멱등 키다. api SelfieJobService의 시도 상한과 맞춘다
Attempt = Annotated[int, Query(ge=1, le=20)]
_UPSTREAM_RESPONSES: dict[int | str, dict[str, Any]] = {
    status.HTTP_503_SERVICE_UNAVAILABLE: {"description": "UPSTREAM_FAILED"},
    status.HTTP_504_GATEWAY_TIMEOUT: {"description": "TIMEOUT"},
}


def get_face_service(request: Request) -> AvatarService:
    service: AvatarService = request.app.state.face_service
    return service


def _failure(status_code: int, reason: FaceFailureReason) -> JSONResponse:
    return JSONResponse(status_code=status_code, content={"reason": reason})


def _failure_of(error: AvatarError) -> JSONResponse:
    return _failure(_STATUS_BY_REASON[error.reason], error.reason)


@FaceRouter.post(
    "/users/{user_id}/faces/jobs",
    status_code=status.HTTP_202_ACCEPTED,
    response_model=HeadJobOut,
    responses={
        status.HTTP_413_CONTENT_TOO_LARGE: {"description": "TOO_LARGE"},
        status.HTTP_415_UNSUPPORTED_MEDIA_TYPE: {"description": "UNSUPPORTED_IMAGE"},
        status.HTTP_422_UNPROCESSABLE_CONTENT: {"description": "UNSUPPORTED_IMAGE, TOO_LARGE, INVALID_REQUEST"},
        **_UPSTREAM_RESPONSES,
    },
)
async def submit(
    request: Request,
    user_id: UserId,
    service: Annotated[AvatarService, Depends(get_face_service)],
    attempt: Attempt = 1,
) -> HeadJobOut | JSONResponse:
    media_type = request.headers.get("content-type", "").split(";", 1)[0].strip().lower()
    if media_type != "application/octet-stream":
        return _failure(status.HTTP_415_UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE")
    if _declared_length(request) > service.max_image_bytes:
        return _failure(status.HTTP_413_CONTENT_TOO_LARGE, "TOO_LARGE")
    try:
        async with asyncio.timeout(service.timeout_s):  # 느린 업로드가 연결을 오래 쥐지 못하게 읽기 구간도 제한한다
            image = await _read_body(request, service.max_image_bytes)
    except ClientDisconnect:
        return JSONResponse(
            status_code=_CLIENT_CLOSED_REQUEST, content={"reason": "CLIENT_CLOSED"}
        )  # 보낼 상대가 없다. 오류 로그·500을 남기지 않는다
    except TimeoutError:
        return _failure(status.HTTP_504_GATEWAY_TIMEOUT, "TIMEOUT")
    if image is None:
        return _failure(status.HTTP_413_CONTENT_TOO_LARGE, "TOO_LARGE")
    rejection = _rejection(image, service.max_image_pixels)
    if rejection is not None:
        return rejection
    try:
        return await service.submit(user_id, image, attempt)
    except AvatarError as error:
        return _failure_of(error)


@FaceRouter.get(
    "/users/{user_id}/faces/jobs/{job_id}",
    response_model=HeadJobOut,
    responses={status.HTTP_404_NOT_FOUND: {"description": "JOB_NOT_FOUND"}, **_UPSTREAM_RESPONSES},
)
async def job(
    user_id: UserId, job_id: UUID, service: Annotated[AvatarService, Depends(get_face_service)]
) -> HeadJobOut | JSONResponse:
    try:
        return await service.job(user_id, str(job_id))
    except AvatarError as error:
        return _failure_of(error)


@FaceRouter.get(
    "/users/{user_id}/faces/jobs/{job_id}/result",
    response_model=HeadOut,
    responses={
        status.HTTP_404_NOT_FOUND: {"description": "JOB_NOT_FOUND"},
        status.HTTP_409_CONFLICT: {"description": "JOB_NOT_READY"},
        **_UPSTREAM_RESPONSES,
    },
)
async def job_result(
    user_id: UserId, job_id: UUID, service: Annotated[AvatarService, Depends(get_face_service)]
) -> HeadOut | JSONResponse:
    try:
        return await service.result(user_id, str(job_id))
    except AvatarError as error:
        return _failure_of(error)


@FaceRouter.get(
    "/users/{user_id}/faces/latest",
    response_model=HeadOut,
    responses={status.HTTP_404_NOT_FOUND: {"description": "HEAD_NOT_FOUND"}, **_UPSTREAM_RESPONSES},
)
async def latest(
    user_id: UserId, service: Annotated[AvatarService, Depends(get_face_service)]
) -> HeadOut | JSONResponse:
    try:
        return await service.latest(user_id)
    except AvatarError as error:
        return _failure_of(error)


@FaceRouter.get(
    "/avatar/bodies/{body_id}",
    response_model=BodyOut,
    responses={status.HTTP_404_NOT_FOUND: {"description": "BODY_NOT_FOUND"}, **_UPSTREAM_RESPONSES},
)
async def body(body_id: BodyId, service: Annotated[AvatarService, Depends(get_face_service)]) -> BodyOut | JSONResponse:
    try:
        return await service.body(body_id)
    except AvatarError as error:
        return _failure_of(error)


def _rejection(image: bytes, max_pixels: int) -> JSONResponse | None:
    """JPEG·PNG가 아니면 415, 머리가 깨졌으면 422 UNSUPPORTED_IMAGE, 가로 × 세로가 상한을 넘으면 422 TOO_LARGE."""
    if not image.startswith((JPEG_SIGNATURE, PNG_SIGNATURE)):
        return _failure(status.HTTP_415_UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE")
    dimensions = image_dimensions(image)
    if dimensions is None:
        return _failure(status.HTTP_422_UNPROCESSABLE_CONTENT, "UNSUPPORTED_IMAGE")
    width, height = dimensions
    if width * height > max_pixels:
        return _failure(status.HTTP_422_UNPROCESSABLE_CONTENT, "TOO_LARGE")
    return None


def _declared_length(request: Request) -> int:
    try:
        return int(request.headers.get("content-length", "0"))
    except ValueError:
        return 0


async def _read_body(request: Request, limit: int) -> bytes | None:
    """Content-Length가 없거나(chunked) 틀려도 상한을 넘는 순간 읽기를 멈춘다. 메모리에서만 모은다."""
    buffer = bytearray()
    async for chunk in request.stream():
        buffer += chunk
        if len(buffer) > limit:
            return None
    return bytes(buffer)
