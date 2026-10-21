"""POST /faces(6.1.16, D-59). 본문은 application/octet-stream(multipart 아님)이고 메모리에서만 읽는다.

UploadFile을 쓰지 않는다(1MB가 넘으면 임시 파일로 넘어간다). 입력 크기 초과 413, 형식 오류 415,
변환 실패 422/503/504 + reason.
"""

from __future__ import annotations

import asyncio
import base64
from typing import Annotated

from fastapi import APIRouter, Depends, Request, status
from fastapi.responses import JSONResponse
from starlette.requests import ClientDisconnect

from runkk_ai.face.schemas import JPEG_SIGNATURE, PNG_SIGNATURE, FaceFailureReason, FaceOut
from runkk_ai.face.service import FaceTransformService

FaceRouter = APIRouter(tags=["face"])

_CLIENT_CLOSED_REQUEST = 499

_STATUS_BY_REASON: dict[FaceFailureReason, int] = {
    "NO_FACE": status.HTTP_422_UNPROCESSABLE_CONTENT,
    "MULTIPLE_FACES": status.HTTP_422_UNPROCESSABLE_CONTENT,
    "UNSUPPORTED_IMAGE": status.HTTP_422_UNPROCESSABLE_CONTENT,
    "TOO_LARGE": status.HTTP_422_UNPROCESSABLE_CONTENT,
    "UPSTREAM_FAILED": status.HTTP_503_SERVICE_UNAVAILABLE,
    "TIMEOUT": status.HTTP_504_GATEWAY_TIMEOUT,
}


def get_face_service(request: Request) -> FaceTransformService:
    service: FaceTransformService = request.app.state.face_service
    return service


def _failure(status_code: int, reason: FaceFailureReason) -> JSONResponse:
    return JSONResponse(status_code=status_code, content={"reason": reason})


@FaceRouter.post(
    "/faces",
    response_model=FaceOut,
    responses={
        status.HTTP_413_CONTENT_TOO_LARGE: {"description": "TOO_LARGE"},
        status.HTTP_415_UNSUPPORTED_MEDIA_TYPE: {"description": "UNSUPPORTED_IMAGE"},
        status.HTTP_422_UNPROCESSABLE_CONTENT: {"description": "NO_FACE, MULTIPLE_FACES, UNSUPPORTED_IMAGE, TOO_LARGE"},
        status.HTTP_503_SERVICE_UNAVAILABLE: {"description": "UPSTREAM_FAILED"},
        status.HTTP_504_GATEWAY_TIMEOUT: {"description": "TIMEOUT"},
    },
)
async def create(
    request: Request,
    service: Annotated[FaceTransformService, Depends(get_face_service)],
) -> FaceOut | JSONResponse:
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
    if not image.startswith((JPEG_SIGNATURE, PNG_SIGNATURE)):
        return _failure(status.HTTP_415_UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE")

    result = await service.transform(image)
    # 셀카 원본의 마지막 참조는 이 지역 변수다(서비스는 반환하며 놓았다). 부위 이미지를 인코딩하기 전에 버린다
    del image
    if result.images is None:
        reason: FaceFailureReason = result.reason or "UPSTREAM_FAILED"
        return _failure(_STATUS_BY_REASON[reason], reason)
    return FaceOut(parts={part: base64.b64encode(png).decode("ascii") for part, png in result.images.images.items()})


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
