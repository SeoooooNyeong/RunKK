"""아바타 라우터: 셀카 접수(202)·작업 상태·결과·최신 머리·기본 몸. 입력 검사와 reason별 상태(14장, D-59)."""

from __future__ import annotations

import httpx
import pytest

from runkk_ai.face import AvatarError
from tests.conftest import BODY_SAMPLE, HEAD_ID, HEAD_SAMPLE, JOB_ID, JPEG_HEAD, PNG_1X1, FakeAvatarClient

OCTET = {"Content-Type": "application/octet-stream"}
JOBS = "/users/42/faces/jobs"


async def test_jpeg_is_accepted_as_a_job(client: httpx.AsyncClient, fake_face: FakeAvatarClient) -> None:
    response = await client.post(JOBS, content=JPEG_HEAD, headers=OCTET)

    assert response.status_code == 202
    assert response.json() == {"jobId": JOB_ID, "status": "QUEUED", "headId": None, "error": None}
    assert fake_face.submitted[0][0] == "42"
    assert fake_face.submitted[0][2] == len(JPEG_HEAD)


async def test_attempt_query_changes_request_id_and_bad_attempt_is_422(
    client: httpx.AsyncClient, fake_face: FakeAvatarClient
) -> None:
    """DV-성-9: api가 명시적 재시도에 attempt(1~20)를 붙인다. 없으면 1이고, 범위 밖이면 Node를 부르지 않는다."""
    assert (await client.post(JOBS, content=JPEG_HEAD, headers=OCTET)).status_code == 202
    assert (await client.post(JOBS + "?attempt=1", content=JPEG_HEAD, headers=OCTET)).status_code == 202
    assert (await client.post(JOBS + "?attempt=2", content=JPEG_HEAD, headers=OCTET)).status_code == 202
    for bad in ("0", "21", "x"):
        assert (await client.post(JOBS + "?attempt=" + bad, content=JPEG_HEAD, headers=OCTET)).status_code == 422

    keys = [k for _, k, _ in fake_face.submitted]
    assert len(keys) == 3
    assert keys[0] == keys[1] != keys[2]


async def test_png_is_accepted(client: httpx.AsyncClient) -> None:
    assert (await client.post(JOBS, content=PNG_1X1, headers=OCTET)).status_code == 202


async def test_declared_length_over_limit_is_413_without_calling_upstream(
    client: httpx.AsyncClient, fake_face: FakeAvatarClient
) -> None:
    body = JPEG_HEAD + b"\x00" * (1024 * 1024)

    response = await client.post(JOBS, content=body, headers=OCTET)

    assert (response.status_code, response.json()) == (413, {"reason": "TOO_LARGE"})
    assert fake_face.submitted == []


async def test_wrong_media_type_is_415(client: httpx.AsyncClient) -> None:
    response = await client.post(JOBS, content=JPEG_HEAD, headers={"Content-Type": "image/jpeg"})

    assert (response.status_code, response.json()) == (415, {"reason": "UNSUPPORTED_IMAGE"})


async def test_gif_is_415(client: httpx.AsyncClient) -> None:
    response = await client.post(JOBS, content=b"GIF89a" + b"\x00" * 10, headers=OCTET)

    assert (response.status_code, response.json()) == (415, {"reason": "UNSUPPORTED_IMAGE"})


async def test_broken_header_is_422_unsupported(client: httpx.AsyncClient) -> None:
    response = await client.post(JOBS, content=b"\xff\xd8\xff" + b"\x00" * 8, headers=OCTET)

    assert (response.status_code, response.json()) == (422, {"reason": "UNSUPPORTED_IMAGE"})


async def test_too_many_pixels_is_422_too_large(client: httpx.AsyncClient, fake_face: FakeAvatarClient) -> None:
    huge = bytearray(JPEG_HEAD)
    huge[0x19:0x1D] = (40000).to_bytes(2, "big") + (40000).to_bytes(2, "big")  # SOF0 높이·너비

    response = await client.post(JOBS, content=bytes(huge), headers=OCTET)

    assert (response.status_code, response.json()) == (422, {"reason": "TOO_LARGE"})
    assert fake_face.submitted == []


@pytest.mark.parametrize(("reason", "status"), [("UPSTREAM_FAILED", 503), ("TIMEOUT", 504)])
async def test_submit_failures(
    client: httpx.AsyncClient, fake_face: FakeAvatarClient, reason: str, status: int
) -> None:
    fake_face.error = AvatarError(reason)  # type: ignore[arg-type]

    response = await client.post(JOBS, content=JPEG_HEAD, headers=OCTET)

    assert (response.status_code, response.json()) == (status, {"reason": reason})


async def test_job_status_and_result(client: httpx.AsyncClient, fake_face: FakeAvatarClient) -> None:
    fake_face.job_status = "SUCCEEDED"

    status = await client.get(f"{JOBS}/{JOB_ID}")
    result = await client.get(f"{JOBS}/{JOB_ID}/result")

    assert status.json() == {"jobId": JOB_ID, "status": "SUCCEEDED", "headId": HEAD_ID, "error": None}
    assert result.status_code == 200
    assert result.json() == HEAD_SAMPLE


async def test_failed_job_status_is_200_with_error(client: httpx.AsyncClient, fake_face: FakeAvatarClient) -> None:
    """실패도 '상태 조회'는 200이다(Node와 같다). 앱은 error.code로 안내 문구를 고른다."""
    fake_face.job_status = "FAILED"

    response = await client.get(f"{JOBS}/{JOB_ID}")

    assert response.status_code == 200
    assert response.json()["status"] == "FAILED"


async def test_result_before_done_is_409(client: httpx.AsyncClient, fake_face: FakeAvatarClient) -> None:
    fake_face.error = AvatarError("JOB_NOT_READY")

    response = await client.get(f"{JOBS}/{JOB_ID}/result")

    assert (response.status_code, response.json()) == (409, {"reason": "JOB_NOT_READY"})


@pytest.mark.parametrize(
    ("path", "reason"),
    [
        (f"{JOBS}/{JOB_ID}", "JOB_NOT_FOUND"),
        ("/users/42/faces/latest", "HEAD_NOT_FOUND"),
        ("/avatar/bodies/daily", "BODY_NOT_FOUND"),
    ],
)
async def test_not_found_reasons(
    client: httpx.AsyncClient, fake_face: FakeAvatarClient, path: str, reason: str
) -> None:
    fake_face.error = AvatarError(reason)  # type: ignore[arg-type]

    response = await client.get(path)

    assert (response.status_code, response.json()) == (404, {"reason": reason})


async def test_latest_and_body(client: httpx.AsyncClient) -> None:
    latest = await client.get("/users/42/faces/latest")
    body = await client.get("/avatar/bodies/daily")

    assert latest.json() == HEAD_SAMPLE
    assert body.json() == BODY_SAMPLE


@pytest.mark.parametrize(
    "path",
    [
        "/users/a:b/faces/latest",  # Node 허용 문자 밖(영문·숫자·_·-)
        "/users/" + "x" * 129 + "/faces/latest",
        f"{JOBS}/not-a-uuid",
        "/avatar/bodies/Daily!",
    ],
)
async def test_bad_path_params_are_422_without_calling_upstream(
    client: httpx.AsyncClient, fake_face: FakeAvatarClient, path: str
) -> None:
    response = await client.get(path)

    assert (response.status_code, response.json()) == (422, {"reason": "INVALID_REQUEST"})
    assert fake_face.calls == []


async def test_upstream_down_on_reads_is_503(client: httpx.AsyncClient, fake_face: FakeAvatarClient) -> None:
    fake_face.error = AvatarError("UPSTREAM_FAILED")

    assert (await client.get("/users/42/faces/latest")).status_code == 503
    assert (await client.get("/avatar/bodies/daily")).status_code == 503
