"""응답 계약 회귀 시험(ai-regression-testing, 14장 계약 시험).

tests/contract/*.json은 서버 AiServerCommentGateway·AiServerFaceGateway 계약 시험이 그대로 읽는 표본이다.
ai 응답이 표본과 한 글자라도 달라지면 여기서 먼저 실패한다. 표본을 고칠 때는 서버 Gateway 시험도 같이 고친다.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import httpx
import pytest

from tests.conftest import JPEG_HEAD, FakeFaceTransformer, FakeLlmClient

SAMPLES_DIR = Path(__file__).parent
SAMPLES = sorted(SAMPLES_DIR.glob("*.json"))
FACE_REASONS = {"NO_FACE", "MULTIPLE_FACES", "UNSUPPORTED_IMAGE", "TOO_LARGE", "UPSTREAM_FAILED", "TIMEOUT"}

IMAGES = {
    "jpeg": JPEG_HEAD,
    "gif": b"GIF89a" + b"\x00" * 10,
    "oversized": JPEG_HEAD + b"\x00" * (1024 * 1024),
}


def _load(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))  # type: ignore[no-any-return]


def _arrange(sample: dict[str, Any], llm: FakeLlmClient, face: FakeFaceTransformer) -> None:
    from runkk_ai.face import FaceTransformError

    scenario: str = sample["scenario"]
    if scenario == "llm_error":
        llm.error = RuntimeError("provider down")
    elif scenario == "llm_timeout":
        llm.error = TimeoutError()
    elif scenario.startswith("reason:"):
        face.error = FaceTransformError(scenario.removeprefix("reason:"))  # type: ignore[arg-type]
    elif scenario == "ok" and sample["endpoint"] == "POST /comments" and sample["status"] == 200:
        llm.text = sample["body"]["text"]


@pytest.mark.parametrize("path", SAMPLES, ids=[p.stem for p in SAMPLES])
async def test_response_matches_sample(
    path: Path,
    client: httpx.AsyncClient,
    anon_client: httpx.AsyncClient,
    fake_llm: FakeLlmClient,
    fake_face: FakeFaceTransformer,
) -> None:
    sample = _load(path)
    _arrange(sample, fake_llm, fake_face)
    caller = anon_client if sample["scenario"] == "no_token" else client  # no_token: X-Internal-Token 없이

    if sample["endpoint"] == "POST /comments":
        response = await caller.post("/comments", json=sample["request"]["json"])
    else:
        response = await caller.post(
            "/faces",
            content=IMAGES[sample["request"]["image"]],
            headers={"Content-Type": "application/octet-stream"},
        )

    assert response.status_code == sample["status"]
    assert response.json() == sample["body"]
    assert response.headers["content-type"] == "application/json"


def test_samples_cover_every_face_reason() -> None:
    reasons = {
        _load(p)["body"].get("reason")
        for p in SAMPLES
        if _load(p)["endpoint"] == "POST /faces" and _load(p)["status"] != 401
    }

    assert reasons - {None} == FACE_REASONS


def test_samples_cover_every_comment_status() -> None:
    statuses = {_load(p)["status"] for p in SAMPLES if _load(p)["endpoint"] == "POST /comments"}

    assert statuses == {200, 401, 422, 503, 504}


@pytest.mark.parametrize("endpoint", ["POST /comments", "POST /faces"])
def test_every_endpoint_has_unauthorized_sample(endpoint: str) -> None:
    """토큰 불일치 401도 다른 실패와 같은 {"reason"} 본문이다. 서버 Gateway는 이 표본으로 설정 오류를 구분한다."""
    bodies = [_load(p)["body"] for p in SAMPLES if _load(p)["endpoint"] == endpoint and _load(p)["status"] == 401]

    assert bodies == [{"reason": "UNAUTHORIZED"}]


def test_every_failure_body_has_only_reason() -> None:
    for path in SAMPLES:
        sample = _load(path)
        if sample["status"] != 200:
            assert set(sample["body"]) == {"reason"}, path.name
