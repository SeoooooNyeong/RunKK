"""NodeAvatarClient: 안선영 님 Node 명세 v1.2의 /api/v1 경로·본문·오류 코드를 그대로 따른다."""

from __future__ import annotations

import re
from typing import Any

import httpx
import pytest

from runkk_ai.face import AvatarError, NodeAvatarClient
from tests.conftest import BODY_SAMPLE, HEAD_ID, HEAD_SAMPLE, JOB_ID, JPEG_HEAD, PNG_1X1

BASE = "http://avatar:3000/api/v1"


def _client(handler: Any, timeout_s: float = 2.0) -> NodeAvatarClient:
    return NodeAvatarClient(base_url=BASE, timeout_s=timeout_s, transport=httpx.MockTransport(handler))


def _json(status: int, body: dict[str, Any]) -> httpx.Response:
    return httpx.Response(status, json=body)


async def test_submit_posts_multipart_with_user_and_idempotency_key() -> None:
    seen: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["content_type"] = request.headers["content-type"]
        seen["body"] = request.read()
        return _json(202, {"jobId": JOB_ID, "userId": "42", "status": "QUEUED", "createdAt": "x", "updatedAt": "x"})

    job = await _client(handler).submit_head("42", "c1a2b3c4-0000-4000-8000-000000000001", JPEG_HEAD)

    assert (job.jobId, job.status, job.headId, job.error) == (JOB_ID, "QUEUED", None, None)
    assert seen["url"] == f"{BASE}/head-jobs"
    assert seen["content_type"].startswith("multipart/form-data")
    body: bytes = seen["body"]
    assert b'name="userId"\r\n\r\n42' in body
    assert b'name="clientRequestId"\r\n\r\nc1a2b3c4-0000-4000-8000-000000000001' in body
    assert b'name="selfie"; filename="selfie.jpg"' in body
    assert b"Content-Type: image/jpeg" in body
    assert JPEG_HEAD in body


async def test_submit_names_png_as_png() -> None:
    seen: dict[str, bytes] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["body"] = request.read()
        return _json(202, {"jobId": JOB_ID, "status": "QUEUED"})

    await _client(handler).submit_head("42", "k", PNG_1X1)

    assert b'filename="selfie.png"' in seen["body"]
    assert b"Content-Type: image/png" in seen["body"]


async def test_job_status_and_result_use_user_query() -> None:
    urls: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        urls.append(str(request.url))
        if request.url.path.endswith("/result"):
            return _json(200, {**HEAD_SAMPLE, "metrics": {"estimatedCredits": 804}})
        return _json(200, {"jobId": JOB_ID, "status": "SUCCEEDED", "headId": HEAD_ID, "error": None})

    client = _client(handler)
    job = await client.job("42", JOB_ID)
    head = await client.job_result("42", JOB_ID)

    assert urls == [f"{BASE}/head-jobs/{JOB_ID}?userId=42", f"{BASE}/head-jobs/{JOB_ID}/result?userId=42"]
    assert (job.status, job.headId) == ("SUCCEEDED", HEAD_ID)
    assert head.headId == HEAD_ID
    assert head.model_dump() == HEAD_SAMPLE  # metrics는 로그용이라 응답에서 뺀다


async def test_failed_job_carries_node_error_code() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return _json(
            200,
            {
                "jobId": JOB_ID,
                "status": "FAILED",
                "error": {"code": "SELFIE_QUALITY_REJECTED", "message": "얼굴이 너무 어두워요"},
            },
        )

    job = await _client(handler).job("42", JOB_ID)

    assert job.status == "FAILED"
    assert job.error is not None
    assert (job.error.code, job.error.message) == ("SELFIE_QUALITY_REJECTED", "얼굴이 너무 어두워요")


async def test_latest_head_accepts_legacy_id_field() -> None:
    """SavedHead는 id(와 headId)를 준다. id만 있어도 headId로 읽는다."""
    saved = {k: v for k, v in HEAD_SAMPLE.items() if k != "headId"} | {"id": HEAD_ID, "imageModel": "gpt-image-2.5"}

    def handler(request: httpx.Request) -> httpx.Response:
        assert str(request.url) == f"{BASE}/heads/latest?userId=42"
        return _json(200, saved)

    head = await _client(handler).latest_head("42")

    assert head.headId == HEAD_ID
    assert head.model_dump()["imageModel"] == "gpt-image-2.5"  # 모르는 필드는 그대로 넘긴다


async def test_body_preset() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        assert str(request.url) == f"{BASE}/bodies/daily"
        return _json(200, BODY_SAMPLE)

    body = await _client(handler).body("daily")

    assert body.pack["id"] == "daily"
    assert body.pack["mobile"]["neck"] == [0.5, 0.083]


@pytest.mark.parametrize(
    ("status", "code", "reason"),
    [
        (404, "JOB_NOT_FOUND", "JOB_NOT_FOUND"),
        (404, "HEAD_NOT_FOUND", "HEAD_NOT_FOUND"),
        (404, "BODY_NOT_FOUND", "BODY_NOT_FOUND"),
        (409, "JOB_NOT_READY", "JOB_NOT_READY"),
        (504, "UPSTREAM_TIMEOUT", "TIMEOUT"),
        (400, "INVALID_USER_ID", "UPSTREAM_FAILED"),  # ai가 먼저 거르므로 나오면 우리 버그다
        (409, "IDEMPOTENCY_CONFLICT", "UPSTREAM_FAILED"),
        (429, "TOO_MANY_ATTEMPTS", "UPSTREAM_FAILED"),
        (503, "SERVICE_UNAVAILABLE", "UPSTREAM_FAILED"),
        (404, "NOT_FOUND", "UPSTREAM_FAILED"),  # Node에 없는 경로를 불렀다
    ],
)
async def test_node_error_codes_map_to_reasons(status: int, code: str, reason: str) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return _json(status, {"code": code, "message": "안내 문구"})

    with pytest.raises(AvatarError) as info:
        await _client(handler).job("42", JOB_ID)

    assert info.value.reason == reason


async def test_non_json_or_incomplete_success_is_upstream_failed() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return _json(200, {"jobId": JOB_ID})  # status 없음

    with pytest.raises(AvatarError) as info:
        await _client(handler).job("42", JOB_ID)
    assert info.value.reason == "UPSTREAM_FAILED"

    def html(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, text="<html>")

    with pytest.raises(AvatarError) as info2:
        await _client(html).latest_head("42")
    assert info2.value.reason == "UPSTREAM_FAILED"


async def test_transport_timeout_is_timeout_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("slow")

    with pytest.raises(TimeoutError):
        await _client(handler).job("42", JOB_ID)


async def test_without_base_url_everything_is_upstream_failed() -> None:
    client = NodeAvatarClient(base_url=None, timeout_s=1.0)

    with pytest.raises(AvatarError) as info:
        await client.submit_head("42", "k", JPEG_HEAD)
    assert info.value.reason == "UPSTREAM_FAILED"


def test_unknown_reason_is_rejected() -> None:
    with pytest.raises(ValueError, match="reason"):
        AvatarError("SOMETHING_ELSE")  # type: ignore[arg-type]


async def test_request_body_has_no_unknown_fields() -> None:
    """Node는 정의 밖 multipart 필드를 쓰지 않는다. 우리도 userId·clientRequestId·selfie만 보낸다."""
    seen: dict[str, bytes] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["body"] = request.read()
        return _json(202, {"jobId": JOB_ID, "status": "QUEUED"})

    await _client(handler).submit_head("42", "k", JPEG_HEAD)
    names = re.findall(rb'form-data; name="([^"]+)"', seen["body"])

    assert sorted(names) == [b"clientRequestId", b"selfie", b"userId"]


async def test_delete_user_calls_node_and_reports_whether_anything_was_there() -> None:
    """탈퇴 파기(S15P31A207-257): Node 사용자 폴더(셀카 작업·머리·옆모습)를 지운다."""
    seen: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(f"{request.method} {request.url}")
        return _json(200, {"userId": "42", "deleted": len(seen) == 1})

    client = _client(handler)

    assert await client.delete_user("42") is True
    assert await client.delete_user("42") is False
    assert seen == [f"DELETE {BASE}/users/42"] * 2


async def test_delete_user_while_generating_is_upstream_failed() -> None:
    """생성 중이면 Node가 409 USER_BUSY. ai는 503으로 돌려 api가 다시 부르게 한다(지웠다고 하지 않는다)."""

    def handler(request: httpx.Request) -> httpx.Response:
        return _json(409, {"code": "USER_BUSY", "message": "만드는 중"})

    with pytest.raises(AvatarError) as info:
        await _client(handler).delete_user("42")
    assert info.value.reason == "UPSTREAM_FAILED"


async def test_delete_user_on_old_node_without_route_is_upstream_failed() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return _json(404, {"code": "NOT_FOUND", "message": "없는 경로"})

    with pytest.raises(AvatarError):
        await _client(handler).delete_user("42")
