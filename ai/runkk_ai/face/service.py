"""얼굴 변환 조정(6.1.16, D-59). 원본 bytes는 지역 변수로만 다루고 디스크·로그에 쓰지 않는다(FR-156)."""

from __future__ import annotations

import asyncio
import logging
import time
from dataclasses import dataclass

from runkk_ai.face.schemas import DEFAULT_MAX_IMAGE_PIXELS, PNG_SIGNATURE, FaceFailureReason, FacePart
from runkk_ai.face.transformer import FaceTransformer, FaceTransformError, SdFacePartImages

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class FaceResult:
    images: SdFacePartImages | None = None
    reason: FaceFailureReason | None = None


class FaceTransformService:
    def __init__(
        self,
        transformer: FaceTransformer,
        timeout_s: float,
        max_image_bytes: int,
        max_image_pixels: int = DEFAULT_MAX_IMAGE_PIXELS,
    ) -> None:
        self._transformer = transformer
        self._timeout_s = timeout_s
        self._max_image_bytes = max_image_bytes
        self._max_image_pixels = max_image_pixels

    @property
    def timeout_s(self) -> float:
        return self._timeout_s

    @property
    def max_image_bytes(self) -> int:
        return self._max_image_bytes

    @property
    def max_image_pixels(self) -> int:
        return self._max_image_pixels

    async def transform(self, image: bytes) -> FaceResult:
        started = time.perf_counter()
        size = len(image)
        detail = ""
        try:
            async with asyncio.timeout(self._timeout_s):
                images = await self._transformer.convert(image)
        except FaceTransformError as exc:
            result = FaceResult(reason=exc.reason)
        except TimeoutError:
            result = FaceResult(reason="TIMEOUT")
        except Exception as exc:
            detail = type(exc).__name__  # 예외 문구에는 외부 응답 본문이 섞일 수 있어 남기지 않는다
            result = FaceResult(reason="UPSTREAM_FAILED")
        else:
            if _complete(images):
                result = FaceResult(images=images)
            else:
                detail = "invalid_parts"
                result = FaceResult(reason="UPSTREAM_FAILED")
        logger.info(
            "face_transform result=%s detail=%s size_bytes=%d latency_ms=%d",
            result.reason or "ok",
            detail,
            size,
            int((time.perf_counter() - started) * 1000),
        )
        return result


def _complete(images: SdFacePartImages) -> bool:
    return set(images.images) == set(FacePart) and all(png.startswith(PNG_SIGNATURE) for png in images.images.values())
