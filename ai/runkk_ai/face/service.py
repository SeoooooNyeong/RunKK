"""아바타 중계 조정. 멱등 키·시간 제한·오류 변환·호출 지표 로그(사용자 식별자·셀카는 남기지 않는다, FR-156)."""

from __future__ import annotations

import asyncio
import hashlib
import logging
import time
import uuid
from collections.abc import Awaitable, Callable

from runkk_ai.face.client import AvatarClient, AvatarError
from runkk_ai.face.schemas import DEFAULT_MAX_IMAGE_PIXELS, BodyOut, HeadJobOut, HeadOut

logger = logging.getLogger(__name__)

# clientRequestId 이름공간. 같은 사용자·같은 셀카면 Node가 같은 작업을 돌려준다(이미지 생성 과금 중복 방지)
_REQUEST_ID_NAMESPACE = uuid.UUID("2f0d8c7a-5b1e-4e2f-9a3c-1d6b7e8f9a0b")


class AvatarService:
    def __init__(
        self,
        client: AvatarClient,
        timeout_s: float,
        max_image_bytes: int,
        max_image_pixels: int = DEFAULT_MAX_IMAGE_PIXELS,
    ) -> None:
        self._client = client
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

    async def submit(self, user_id: str, image: bytes) -> HeadJobOut:
        key = request_id(user_id, image)
        job = await self._call("submit", lambda: self._client.submit_head(user_id, key, image), size=len(image))
        return job

    async def job(self, user_id: str, job_id: str) -> HeadJobOut:
        return await self._call("job", lambda: self._client.job(user_id, job_id), job_id=job_id)

    async def result(self, user_id: str, job_id: str) -> HeadOut:
        return await self._call("result", lambda: self._client.job_result(user_id, job_id), job_id=job_id)

    async def latest(self, user_id: str) -> HeadOut:
        return await self._call("latest", lambda: self._client.latest_head(user_id))

    async def body(self, body_id: str) -> BodyOut:
        return await self._call("body", lambda: self._client.body(body_id))

    async def purge_user(self, user_id: str) -> int:
        """탈퇴 파기(privacy.WithdrawalService 규약). 사용자 폴더 하나라 지웠으면 1, 없었으면 0. 실패는 AvatarError."""
        deleted = await self._call("delete", lambda: self._client.delete_user(user_id))
        return 1 if deleted else 0

    async def aclose(self) -> None:
        await self._client.aclose()

    async def _call[T](self, op: str, call: Callable[[], Awaitable[T]], *, job_id: str = "", size: int = 0) -> T:
        started = time.perf_counter()
        detail = ""
        error: AvatarError | None = None
        value: T | None = None
        try:
            async with asyncio.timeout(self._timeout_s):
                value = await call()
        except AvatarError as exc:
            error = exc
        except TimeoutError:
            error = AvatarError("TIMEOUT")
        except Exception as exc:
            detail = type(exc).__name__  # 예외 문구에는 외부 응답 본문이 섞일 수 있어 남기지 않는다
            error = AvatarError("UPSTREAM_FAILED")
        if isinstance(value, HeadJobOut):
            job_id = value.jobId
        logger.info(
            "avatar_call op=%s result=%s detail=%s job_id=%s size_bytes=%d latency_ms=%d",
            op,
            error.reason if error else "ok",
            detail,
            job_id,
            size,
            int((time.perf_counter() - started) * 1000),
        )
        if error is not None:
            raise error
        assert value is not None  # noqa: S101 - 예외가 없으면 호출이 값을 돌려줬다
        return value


def request_id(user_id: str, image: bytes) -> str:
    """사용자와 셀카 내용으로 정해지는 UUID v5. 셀카 자체는 쓰지 않고 해시만 쓴다."""
    digest = hashlib.sha256(image).hexdigest()
    return str(uuid.uuid5(_REQUEST_ID_NAMESPACE, f"{user_id}:{digest}"))
