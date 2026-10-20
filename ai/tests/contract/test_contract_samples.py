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
    path: Path, client: httpx.AsyncClient, fake_llm: FakeLlmClient, fake_face: FakeFaceTransformer
) -> None:
    sample = _load(path)
    _arrange(sample, fake_llm, fake_face)

    if sample["endpoint"] == "POST /comments":
        response = await client.post("/comments", json=sample["request"]["json"])
    else:
        response = await client.post(
            "/faces",
            content=IMAGES[sample["request"]["image"]],
            headers={"Content-Type": "application/octet-stream"},
        )

    assert response.status_code == sample["status"]
    assert response.json() == sample["body"]
    assert response.headers["content-type"] == "application/json"


def test_samples_cover_every_face_reason() -> None:
    reasons = {_load(p)["body"].get("reason") for p in SAMPLES if _load(p)["endpoint"] == "POST /faces"}

    assert reasons - {None} == FACE_REASONS


def test_samples_cover_every_comment_status() -> None:
    statuses = {_load(p)["status"] for p in SAMPLES if _load(p)["endpoint"] == "POST /comments"}

    assert statuses == {200, 422, 503, 504}


def test_every_failure_body_has_only_reason() -> None:
    for path in SAMPLES:
        sample = _load(path)
        if sample["status"] != 200:
            assert set(sample["body"]) == {"reason"}, path.name
