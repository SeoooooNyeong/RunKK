"""아바타(Node) 서버 클라이언트. 기준 경로 {base_url} = http://avatar:3000/api/v1 (Node 명세 v1.2 1장).

호출: POST /head-jobs(multipart userId·clientRequestId·selfie) · GET /head-jobs/{id} · GET /head-jobs/{id}/result ·
GET /heads/latest · GET /bodies/{id}. 셀카는 메모리에서만 다루고 디스크·로그에 쓰지 않는다(FR-156).
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any, cast

import httpx
from pydantic import BaseModel, ValidationError

from runkk_ai.face.schemas import (
    FACE_FAILURE_REASONS,
    PNG_SIGNATURE,
    BodyOut,
    FaceFailureReason,
    HeadJobOut,
    HeadOut,
)

# Node 오류 code(명세 8장) → ai reason. 표에 없는 code(INVALID_USER_ID, IDEMPOTENCY_CONFLICT, TOO_MANY_ATTEMPTS …)는
# ai가 먼저 거르거나 중계 쪽 문제이므로 UPSTREAM_FAILED다
_REASON_BY_NODE_CODE: dict[str, FaceFailureReason] = {
    "JOB_NOT_FOUND": "JOB_NOT_FOUND",
    "HEAD_NOT_FOUND": "HEAD_NOT_FOUND",
    "BODY_NOT_FOUND": "BODY_NOT_FOUND",
    "JOB_NOT_READY": "JOB_NOT_READY",
    "UPSTREAM_TIMEOUT": "TIMEOUT",
}


class AvatarError(Exception):
    """아바타 호출 실패 사유. reason은 FaceRouter 응답의 reason 8종 중 하나다."""

    def __init__(self, reason: FaceFailureReason) -> None:
        if reason not in FACE_FAILURE_REASONS:
            raise ValueError(f"정의 밖 reason: {reason}")
        super().__init__(reason)
        self.reason: FaceFailureReason = reason


class AvatarClient(ABC):
    """실패는 AvatarError(reason), 시간 초과는 TimeoutError로 알린다."""

    @abstractmethod
    async def submit_head(self, user_id: str, client_request_id: str, image: bytes) -> HeadJobOut: ...

    @abstractmethod
    async def job(self, user_id: str, job_id: str) -> HeadJobOut: ...

    @abstractmethod
    async def job_result(self, user_id: str, job_id: str) -> HeadOut: ...

    @abstractmethod
    async def latest_head(self, user_id: str) -> HeadOut: ...

    @abstractmethod
    async def body(self, body_id: str) -> BodyOut: ...

    async def aclose(self) -> None:
        return None


class NodeAvatarClient(AvatarClient):
    def __init__(
        self, base_url: str | None, timeout_s: float, transport: httpx.AsyncBaseTransport | None = None
    ) -> None:
        self._base_url = base_url.rstrip("/") if base_url else None
        self._client = httpx.AsyncClient(timeout=httpx.Timeout(timeout_s), transport=transport, follow_redirects=False)

    async def submit_head(self, user_id: str, client_request_id: str, image: bytes) -> HeadJobOut:
        is_png = image.startswith(PNG_SIGNATURE)
        files = {"selfie": ("selfie.png" if is_png else "selfie.jpg", image, "image/png" if is_png else "image/jpeg")}
        response = await self._request(
            "POST", "/head-jobs", data={"userId": user_id, "clientRequestId": client_request_id}, files=files
        )
        return _parse(response, HeadJobOut)

    async def job(self, user_id: str, job_id: str) -> HeadJobOut:
        return _parse(await self._request("GET", f"/head-jobs/{job_id}", params={"userId": user_id}), HeadJobOut)

    async def job_result(self, user_id: str, job_id: str) -> HeadOut:
        return _parse(await self._request("GET", f"/head-jobs/{job_id}/result", params={"userId": user_id}), HeadOut)

    async def latest_head(self, user_id: str) -> HeadOut:
        return _parse(await self._request("GET", "/heads/latest", params={"userId": user_id}), HeadOut)

    async def body(self, body_id: str) -> BodyOut:
        return _parse(await self._request("GET", f"/bodies/{body_id}"), BodyOut)

    async def aclose(self) -> None:
        await self._client.aclose()

    async def _request(self, method: str, path: str, **kwargs: Any) -> httpx.Response:
        if self._base_url is None:
            raise AvatarError("UPSTREAM_FAILED")
        try:
            return await self._client.request(method, self._base_url + path, **kwargs)
        except httpx.TimeoutException as exc:
            raise TimeoutError from exc
        except httpx.HTTPError as exc:
            raise AvatarError("UPSTREAM_FAILED") from exc


def _parse[T: BaseModel](response: httpx.Response, model: type[T]) -> T:
    if not httpx.codes.is_success(response.status_code):
        raise AvatarError(_REASON_BY_NODE_CODE.get(_code_of(response), "UPSTREAM_FAILED"))
    try:
        return model.model_validate(response.json())
    except (ValueError, ValidationError) as exc:  # JSON이 아니거나 계약 밖 본문
        raise AvatarError("UPSTREAM_FAILED") from exc


def _code_of(response: httpx.Response) -> str:
    try:
        code = cast(dict[str, Any], response.json()).get("code")
    except (ValueError, AttributeError):
        return ""
    return code if isinstance(code, str) else ""
