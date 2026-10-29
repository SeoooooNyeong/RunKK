"""응답 계약 회귀 시험(ai-regression-testing, 14장 계약 시험).

tests/contract/*.json은 서버 AiServerCommentGateway·AiServerAvatarGateway 계약 시험이 그대로 읽는 표본이다.
ai 응답이 표본과 한 글자라도 달라지면 여기서 먼저 실패한다. 표본을 고칠 때는 서버 Gateway 시험도 같이 고친다.

sessions-*.json(RUN바디 1회 분석)·months-*.json(월간 RUN바디)은
서버 쪽 Gateway가 생기면(S15P31A207-163) 같은 방식으로 읽는다.
request.fixture는 tests/fixtures/{이름}.json 본문, request.override는 그 위에 덮어쓸 필드,
setup은 먼저 분석해 둘 fixture다.
"""

from __future__ import annotations

import asyncio
import json
from collections.abc import Sequence
from pathlib import Path
from typing import Any

import httpx
import pytest
from fastapi import FastAPI

from runkk_ai.runbody import InMemoryResultRepository, RunbodyService, SessionIn, SessionResultOut, get_runbody_service
from runkk_ai.runbody.schemas import Metrics
from tests.conftest import JPEG_HEAD, FakeAvatarClient, FakeLlmClient

FIXTURES_DIR = Path(__file__).resolve().parents[1] / "fixtures"
FACE_ENDPOINTS = (
    "POST /users/{userId}/faces/jobs",
    "GET /users/{userId}/faces/jobs/{jobId}",
    "GET /users/{userId}/faces/jobs/{jobId}/result",
    "GET /users/{userId}/faces/latest",
    "GET /avatar/bodies/{bodyId}",
)
SESSION_ENDPOINTS = (
    "POST /sessions/{sessionId}/analyze",
    "GET /sessions/{sessionId}/status",
    "GET /sessions/{sessionId}",
    "GET /users/{userId}/months/{yearMonth}",
)

SAMPLES_DIR = Path(__file__).parent
SAMPLES = sorted(SAMPLES_DIR.glob("*.json"))
FACE_REASONS = {
    "UNSUPPORTED_IMAGE",
    "TOO_LARGE",
    "UPSTREAM_FAILED",
    "TIMEOUT",
    "JOB_NOT_FOUND",
    "JOB_NOT_READY",
    "HEAD_NOT_FOUND",
    "BODY_NOT_FOUND",
}

_HUGE = bytearray(JPEG_HEAD)
_HUGE[0x19:0x1D] = (40000).to_bytes(2, "big") * 2  # SOF0 높이·너비
IMAGES = {
    "jpeg": JPEG_HEAD,
    "gif": b"GIF89a" + b"\x00" * 10,
    "oversized": JPEG_HEAD + b"\x00" * (1024 * 1024),
    "broken": b"\xff\xd8\xff" + b"\x00" * 8,
    "huge_pixels": bytes(_HUGE),
}


def _load(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))  # type: ignore[no-any-return]


def _arrange(sample: dict[str, Any], llm: FakeLlmClient, face: FakeAvatarClient) -> None:
    from runkk_ai.face import AvatarError

    scenario: str = sample["scenario"]
    if scenario == "llm_error":
        llm.error = RuntimeError("provider down")
    elif scenario == "llm_timeout":
        llm.error = TimeoutError()
    elif scenario.startswith("reason:"):
        face.error = AvatarError(scenario.removeprefix("reason:"))  # type: ignore[arg-type]
    elif scenario.startswith("job:"):
        face.job_status = scenario.removeprefix("job:")
        if face.job_status == "FAILED":
            face.job_error = sample["body"]["error"]
    elif scenario == "ok" and sample["endpoint"] == "POST /comments" and sample["status"] == 200:
        llm.text = sample["body"]["text"]


def _fixture_body(name: str, override: dict[str, Any] | None = None) -> bytes:
    body: dict[str, Any] = json.loads((FIXTURES_DIR / f"{name}.json").read_text(encoding="utf-8"))
    body.update(override or {})
    return json.dumps(body, ensure_ascii=False).encode()


async def _never_finishes(
    session: SessionIn, history: Sequence[SessionResultOut], load: Metrics | None
) -> SessionResultOut:
    await asyncio.Event().wait()
    raise AssertionError  # 도달하지 않는다


async def _call_sessions(
    sample: dict[str, Any], caller: httpx.AsyncClient, client: httpx.AsyncClient, app: FastAPI, service: RunbodyService
) -> httpx.Response:
    request: dict[str, Any] = sample["request"]
    for name in sample.get("setup", []):
        body = json.loads(_fixture_body(name))
        await client.post(f"/sessions/{body['sessionId']}/analyze", content=_fixture_body(name))
        await service.wait(body["sessionId"])
    if sample["scenario"] == "pending":
        pending = RunbodyService(InMemoryResultRepository(), analyzer=_never_finishes)
        app.dependency_overrides[get_runbody_service] = lambda: pending
        await client.post(f"/sessions/{request['sessionId']}/analyze", content=_fixture_body(request["fixture"]))
    path = sample["endpoint"].split(" ", 1)[1]
    for name, value in request.items():
        if isinstance(value, str):
            path = path.replace("{" + name + "}", value)
    if sample["endpoint"].startswith("GET"):
        response = await caller.get(path)
    else:
        headers = {"Content-Type": "application/json"}
        if "declaredLength" in request:
            headers["Content-Length"] = str(request["declaredLength"])
            response = await caller.post(path, content=b"{}", headers=headers)
        else:
            response = await caller.post(
                path, content=_fixture_body(request["fixture"], request.get("override")), headers=headers
            )
    if sample["scenario"] == "pending":
        await pending.aclose()
    return response


def _fill(endpoint: str, request: dict[str, Any]) -> str:
    path = endpoint.split(" ", 1)[1]
    for name, value in request.items():
        if isinstance(value, str):
            path = path.replace("{" + name + "}", value)
    return path


async def _call_faces(sample: dict[str, Any], caller: httpx.AsyncClient) -> httpx.Response:
    path = _fill(sample["endpoint"], sample["request"])
    if sample["endpoint"].startswith("GET"):
        return await caller.get(path)
    return await caller.post(
        path, content=IMAGES[sample["request"]["image"]], headers={"Content-Type": "application/octet-stream"}
    )


@pytest.mark.parametrize("path", SAMPLES, ids=[p.stem for p in SAMPLES])
async def test_response_matches_sample(
    path: Path,
    client: httpx.AsyncClient,
    anon_client: httpx.AsyncClient,
    app: FastAPI,
    runbody_service: RunbodyService,
    fake_llm: FakeLlmClient,
    fake_face: FakeAvatarClient,
) -> None:
    sample = _load(path)
    _arrange(sample, fake_llm, fake_face)
    caller = anon_client if sample["scenario"] == "no_token" else client  # no_token: X-Internal-Token 없이

    if sample["endpoint"] in SESSION_ENDPOINTS:
        response = await _call_sessions(sample, caller, client, app, runbody_service)
    elif sample["endpoint"] == "POST /comments":
        response = await caller.post("/comments", json=sample["request"]["json"])
    else:
        response = await _call_faces(sample, caller)

    assert response.status_code == sample["status"]
    assert response.json() == sample["body"]
    assert response.headers["content-type"] == "application/json"


def test_samples_cover_every_face_reason() -> None:
    reasons = {
        _load(p)["body"].get("reason")
        for p in SAMPLES
        if _load(p)["endpoint"] in FACE_ENDPOINTS and _load(p)["status"] != 401
    }

    assert reasons - {None} == FACE_REASONS


def test_samples_cover_every_face_status() -> None:
    statuses = {
        endpoint: {_load(p)["status"] for p in SAMPLES if _load(p)["endpoint"] == endpoint}
        for endpoint in FACE_ENDPOINTS
    }

    assert statuses == {
        "POST /users/{userId}/faces/jobs": {202, 401, 413, 415, 422, 503, 504},
        "GET /users/{userId}/faces/jobs/{jobId}": {200, 401, 404},
        "GET /users/{userId}/faces/jobs/{jobId}/result": {200, 401, 404, 409},
        "GET /users/{userId}/faces/latest": {200, 401, 404},
        "GET /avatar/bodies/{bodyId}": {200, 401, 404},
    }


def test_samples_cover_every_comment_status() -> None:
    statuses = {_load(p)["status"] for p in SAMPLES if _load(p)["endpoint"] == "POST /comments"}

    assert statuses == {200, 401, 422, 503, 504}


def test_samples_cover_every_session_status() -> None:
    statuses = {
        endpoint: {_load(p)["status"] for p in SAMPLES if _load(p)["endpoint"] == endpoint}
        for endpoint in SESSION_ENDPOINTS
    }

    assert statuses == {
        "POST /sessions/{sessionId}/analyze": {202, 401, 409, 413, 422},
        "GET /sessions/{sessionId}/status": {200, 401, 404},
        "GET /sessions/{sessionId}": {200, 401, 404, 409},
        "GET /users/{userId}/months/{yearMonth}": {200, 401, 422},
    }


@pytest.mark.parametrize("endpoint", ["POST /comments", *FACE_ENDPOINTS, *SESSION_ENDPOINTS])
def test_every_endpoint_has_unauthorized_sample(endpoint: str) -> None:
    """토큰 불일치 401도 다른 실패와 같은 {"reason"} 본문이다. 서버 Gateway는 이 표본으로 설정 오류를 구분한다."""
    bodies = [_load(p)["body"] for p in SAMPLES if _load(p)["endpoint"] == endpoint and _load(p)["status"] == 401]

    assert bodies == [{"reason": "UNAUTHORIZED"}]


def test_every_failure_body_has_only_reason() -> None:
    for path in SAMPLES:
        sample = _load(path)
        if sample["status"] >= 400:
            assert set(sample["body"]) == {"reason"}, path.name
