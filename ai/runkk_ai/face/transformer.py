"""얼굴 변환 계약과 구현(6.1.16). 원본 bytes는 메모리에서만 다루고 디스크·로그에 쓰지 않는다(FR-156, D-59)."""

from __future__ import annotations

import asyncio
import base64
import binascii
from abc import ABC, abstractmethod
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from typing import cast

import anyio.to_thread
import httpx

from runkk_ai.face.schemas import FACE_FAILURE_REASONS, FaceFailureReason, FacePart

# 외부 API가 알려 줄 수 있는 입력 쪽 실패. 그 밖의 응답은 UPSTREAM_FAILED로 본다
_CLIENT_SIDE_REASONS = frozenset({"NO_FACE", "MULTIPLE_FACES", "UNSUPPORTED_IMAGE", "TOO_LARGE"})


@dataclass(frozen=True, slots=True)
class SdFacePartImages:
    """부위별 PNG bytes. api avatar.SdFaceParts(저장 키)와 이름을 나눴다."""

    images: Mapping[FacePart, bytes]


class FaceTransformError(Exception):
    """변환기가 알린 실패 사유. reason은 FaceRouter 응답의 reason 6종 중 하나다."""

    def __init__(self, reason: FaceFailureReason) -> None:
        if reason not in FACE_FAILURE_REASONS:
            raise ValueError(f"정의 밖 reason: {reason}")
        super().__init__(reason)
        self.reason: FaceFailureReason = reason


class FaceTransformer(ABC):
    @abstractmethod
    async def convert(self, image: bytes) -> SdFacePartImages:
        """실패는 FaceTransformError(reason), 시간 초과는 TimeoutError로 알린다."""

    async def aclose(self) -> None:
        return None


class ExternalFaceApi(FaceTransformer):
    """외부 얼굴 변환 API(C-011 선정 전 임시 계약).

    POST {base_url}, 본문 application/octet-stream, Authorization: Bearer {api_key}.
    200 {"parts": {"EYES": base64 PNG, ...}}, 4xx {"reason": NO_FACE 등}. 재시도하지 않는다.
    """

    def __init__(
        self,
        base_url: str | None,
        api_key: str | None,
        timeout_s: float,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._base_url = base_url
        headers = {"Authorization": f"Bearer {api_key}"} if api_key else {}
        self._client = httpx.AsyncClient(
            timeout=httpx.Timeout(timeout_s), headers=headers, transport=transport, follow_redirects=False
        )

    async def convert(self, image: bytes) -> SdFacePartImages:
        if not self._base_url:
            raise FaceTransformError("UPSTREAM_FAILED")
        try:
            response = await self._client.post(
                self._base_url, content=image, headers={"Content-Type": "application/octet-stream"}
            )
        except httpx.TimeoutException as exc:
            raise TimeoutError from exc
        except httpx.HTTPError as exc:
            raise FaceTransformError("UPSTREAM_FAILED") from exc
        if response.status_code == httpx.codes.OK:
            return _decode_parts(response)
        if httpx.codes.is_client_error(response.status_code):
            reason = _reason_of(response)
            if reason in _CLIENT_SIDE_REASONS:
                raise FaceTransformError(cast(FaceFailureReason, reason))
        raise FaceTransformError("UPSTREAM_FAILED")

    async def aclose(self) -> None:
        await self._client.aclose()


class LocalSdModel(FaceTransformer):
    """팀 운영 모델(C-011에서 고르면 사용). 동기 추론을 스레드에서 한 번에 하나씩 돌려 이벤트 루프를 막지 않는다."""

    def __init__(self, pipeline: Callable[[bytes], Mapping[FacePart, bytes]]) -> None:
        self._pipeline = pipeline
        self._semaphore = asyncio.Semaphore(1)

    async def convert(self, image: bytes) -> SdFacePartImages:
        async with self._semaphore:
            parts = await anyio.to_thread.run_sync(self._pipeline, image)
        return SdFacePartImages(images=dict(parts))


def _decode_parts(response: httpx.Response) -> SdFacePartImages:
    try:
        encoded = response.json()["parts"]
        images = {FacePart(name): base64.b64decode(value, validate=True) for name, value in encoded.items()}
    except (ValueError, KeyError, TypeError, AttributeError, binascii.Error) as exc:
        raise FaceTransformError("UPSTREAM_FAILED") from exc
    return SdFacePartImages(images=images)


def _reason_of(response: httpx.Response) -> str | None:
    try:
        reason = response.json().get("reason")
    except (ValueError, AttributeError):
        return None
    return reason if isinstance(reason, str) else None
