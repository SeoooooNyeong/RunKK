"""AvatarService: 멱등 키, 시간 제한, 오류 변환, 셀카를 로그·디스크에 남기지 않음(FR-156, D-59, T-17)."""

from __future__ import annotations

import base64
import builtins
import logging
import tempfile
import uuid
from typing import Any

import httpx
import pytest

from runkk_ai.face import AvatarError, AvatarService
from tests.conftest import HEAD_ID, JOB_ID, JPEG_HEAD, PNG_1X1, FakeAvatarClient

SELFIE = JPEG_HEAD + b"SELFIE-SECRET-PIXELS" * 10


def _service(client: FakeAvatarClient, **kwargs: Any) -> AvatarService:
    kwargs.setdefault("timeout_s", 1.0)
    kwargs.setdefault("max_image_bytes", 1024 * 1024)
    return AvatarService(client=client, **kwargs)


async def test_submit_passes_user_and_returns_job() -> None:
    client = FakeAvatarClient()

    job = await _service(client).submit("42", SELFIE)

    assert (job.jobId, job.status) == (JOB_ID, "QUEUED")
    assert client.submitted[0][0] == "42"
    assert client.submitted[0][2] == len(SELFIE)


async def test_same_user_and_photo_give_the_same_request_id() -> None:
    """api가 같은 요청을 다시 보내도 Node가 같은 작업을 돌려주게(이미지 생성 과금 중복 방지) 키를 내용에서 만든다."""
    client = FakeAvatarClient()
    service = _service(client)

    await service.submit("42", SELFIE)
    await service.submit("42", SELFIE)
    await service.submit("42", SELFIE + b"x")
    await service.submit("43", SELFIE)

    keys = [k for _, k, _ in client.submitted]
    assert keys[0] == keys[1]
    assert len({keys[0], keys[2], keys[3]}) == 3
    assert all(uuid.UUID(k).version == 5 for k in keys)


async def test_explicit_retry_attempt_gives_a_new_request_id() -> None:
    """DV-9: 같은 사진을 사용자가 명시적으로 다시 시도하면(attempt 2 이상) Node가 새 작업을 만들도록 다른 키를 쓴다.
    attempt 1은 기존 키와 같다(같은 요청 재전송은 같은 작업)."""
    client = FakeAvatarClient()
    service = _service(client)

    await service.submit("42", SELFIE)
    await service.submit("42", SELFIE, attempt=1)
    await service.submit("42", SELFIE, attempt=2)
    await service.submit("42", SELFIE, attempt=2)
    await service.submit("42", SELFIE, attempt=3)

    keys = [k for _, k, _ in client.submitted]
    assert keys[0] == keys[1]
    assert keys[2] == keys[3]
    assert len({keys[0], keys[2], keys[4]}) == 3


def test_exposes_limits() -> None:
    service = _service(FakeAvatarClient(), max_image_bytes=4321, max_image_pixels=99)

    assert (service.max_image_bytes, service.max_image_pixels, service.timeout_s) == (4321, 99, 1.0)


async def test_slow_upstream_is_timeout() -> None:
    service = _service(FakeAvatarClient(delay_s=1.0), timeout_s=0.05)

    with pytest.raises(AvatarError) as info:
        await service.submit("42", SELFIE)
    assert info.value.reason == "TIMEOUT"


async def test_client_timeout_error_is_timeout() -> None:
    service = _service(FakeAvatarClient(error=TimeoutError()))

    with pytest.raises(AvatarError) as info:
        await service.latest("42")
    assert info.value.reason == "TIMEOUT"


async def test_known_reason_passes_through() -> None:
    service = _service(FakeAvatarClient(error=AvatarError("JOB_NOT_READY")))

    with pytest.raises(AvatarError) as info:
        await service.result("42", JOB_ID)
    assert info.value.reason == "JOB_NOT_READY"


async def test_unexpected_exception_is_upstream_failed_and_logs_only_type(caplog: pytest.LogCaptureFixture) -> None:
    caplog.set_level(logging.DEBUG)
    service = _service(FakeAvatarClient(error=RuntimeError("SELFIE-SECRET in message")))

    with pytest.raises(AvatarError) as info:
        await service.submit("42", SELFIE)

    assert info.value.reason == "UPSTREAM_FAILED"
    assert "result=UPSTREAM_FAILED" in caplog.text
    assert "RuntimeError" in caplog.text
    assert "SELFIE-SECRET" not in caplog.text


async def test_log_has_result_but_no_image_or_user(caplog: pytest.LogCaptureFixture) -> None:
    caplog.set_level(logging.DEBUG)
    client = FakeAvatarClient(job_status="SUCCEEDED")
    service = _service(client)

    await service.submit("42", SELFIE)
    await service.result("42", JOB_ID)

    assert "avatar_call op=submit result=ok" in caplog.text
    assert f"job_id={JOB_ID}" in caplog.text
    assert "SELFIE-SECRET" not in caplog.text
    assert base64.b64encode(SELFIE).decode()[:16] not in caplog.text
    assert base64.b64encode(PNG_1X1).decode()[:16] not in caplog.text
    assert "user_id=42" not in caplog.text  # 회원 식별자는 로그에 남기지 않는다


async def test_result_and_latest_and_body(caplog: pytest.LogCaptureFixture) -> None:
    client = FakeAvatarClient(job_status="SUCCEEDED")
    service = _service(client)

    assert (await service.job("42", JOB_ID)).headId == HEAD_ID
    assert (await service.result("42", JOB_ID)).headId == HEAD_ID
    assert (await service.latest("42")).headId == HEAD_ID
    assert (await service.body("daily")).pack["id"] == "daily"
    assert client.calls == ["job", "result", "latest", "body"]


async def test_selfie_never_touches_disk(
    client: httpx.AsyncClient, monkeypatch: pytest.MonkeyPatch, caplog: pytest.LogCaptureFixture
) -> None:
    """T-17의 ai 쪽 단위 시험: 요청 처리 중 파일을 쓰거나 임시 파일을 만들지 않는다."""
    caplog.set_level(logging.DEBUG)
    writes: list[Any] = []
    real_open = builtins.open

    def guarded_open(file: Any, mode: str = "r", *args: Any, **kwargs: Any) -> Any:
        if any(flag in mode for flag in "wax+"):
            writes.append(file)
        return real_open(file, mode, *args, **kwargs)

    def forbidden(*args: Any, **kwargs: Any) -> Any:
        writes.append("tempfile")
        raise AssertionError("임시 파일을 만들면 안 된다")

    monkeypatch.setattr(builtins, "open", guarded_open)
    for name in ("TemporaryFile", "NamedTemporaryFile", "SpooledTemporaryFile", "mkstemp"):
        monkeypatch.setattr(tempfile, name, forbidden)

    response = await client.post(
        "/users/42/faces/jobs", content=SELFIE, headers={"Content-Type": "application/octet-stream"}
    )

    assert response.status_code == 202
    assert writes == []
    assert "SELFIE-SECRET" not in caplog.text


async def test_purge_user_counts_deleted_folder(caplog: pytest.LogCaptureFixture) -> None:
    """WithdrawalService가 부르는 규약(purge_user → 지운 수). 폴더 하나라 1 또는 0."""
    caplog.set_level(logging.INFO)
    client = FakeAvatarClient()
    service = _service(client)

    assert await service.purge_user("42") == 1
    assert await service.purge_user("42") == 0
    assert client.deleted == ["42", "42"]
    assert "avatar_call op=delete result=ok" in caplog.text
    assert "42" not in caplog.text.replace("avatar_call", "")


async def test_purge_user_failure_raises() -> None:
    service = _service(FakeAvatarClient(error=AvatarError("UPSTREAM_FAILED")))

    with pytest.raises(AvatarError):
        await service.purge_user("42")
