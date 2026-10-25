"""/sessions 엔드포인트 시험. 실패 본문은 모두 {"reason"} 한 필드다."""

from __future__ import annotations

import asyncio
import json
from collections.abc import AsyncIterator
from typing import Any

import httpx

from runkk_ai.runbody import RunbodyService
from tests.runbody.conftest import FakeAnalyzer

FIRST = "run-20261018"
SECOND = "run-20261019"


def _content(body: dict[str, Any]) -> bytes:
    return json.dumps(body, ensure_ascii=False).encode()


async def _analyze(client: httpx.AsyncClient, body: dict[str, Any], session_id: str | None = None) -> httpx.Response:
    return await client.post(
        f"/sessions/{session_id or body['sessionId']}/analyze",
        content=_content(body),
        headers={"Content-Type": "application/json"},
    )


async def test_analyze_accepts_then_result_is_ready(
    client: httpx.AsyncClient, runbody_service: RunbodyService, second_run_body: dict[str, Any]
) -> None:
    accepted = await _analyze(client, second_run_body)

    assert accepted.status_code == 202
    assert accepted.json() == {"sessionId": SECOND, "status": "pending", "engineVersion": "0.2"}

    await runbody_service.wait(SECOND)
    status = await client.get(f"/sessions/{SECOND}/status")
    result = await client.get(f"/sessions/{SECOND}")

    assert status.status_code == 200
    assert status.json()["status"] == "done"
    assert status.json()["errorCode"] is None
    assert result.status_code == 200
    body = result.json()
    assert body["schemaVersion"] == 2
    assert body["feedback"]["headline"] == "출발이 너무 빨랐어요"
    assert body["coaching"]["flags"] == ["warmup_missing", "positive_split", "overstride"]
    assert body["axes"]["form"] == {"score": 62, "ref": 60, "value": 165.0, "label": "보폭이 긴 편", "level": "warn"}


async def test_second_run_compares_with_previous_run(
    client: httpx.AsyncClient,
    runbody_service: RunbodyService,
    first_run_body: dict[str, Any],
    second_run_body: dict[str, Any],
) -> None:
    await _analyze(client, first_run_body)
    await runbody_service.wait(FIRST)
    await _analyze(client, second_run_body)
    await runbody_service.wait(SECOND)

    body = (await client.get(f"/sessions/{SECOND}")).json()

    assert body["prev"] == {
        "sessionId": FIRST,
        "distanceMeters": 2027,
        "paceSecondsPerKm": 379,
        "recoveryHeartRate1m": -14,
    }
    assert body["feedback"]["good"] == "회복 심박이 좋아졌어요 (1분 -27, 지난번 -14)"


async def test_history_ignores_later_runs_and_other_users(
    client: httpx.AsyncClient,
    runbody_service: RunbodyService,
    first_run_body: dict[str, Any],
    second_run_body: dict[str, Any],
) -> None:
    """나중에 뛴 러닝이 먼저 분석돼도 지난 러닝으로 잡지 않는다. 다른 사용자 기록도 섞지 않는다."""
    other = {**first_run_body, "sessionId": "other-user-run", "userId": "u_other"}
    await _analyze(client, second_run_body)
    await _analyze(client, other)
    await runbody_service.wait(SECOND)
    await runbody_service.wait("other-user-run")
    await _analyze(client, first_run_body)
    await runbody_service.wait(FIRST)

    assert (await client.get(f"/sessions/{FIRST}")).json()["prev"] is None
    assert (await client.get(f"/sessions/{SECOND}")).json()["prev"] is None


async def test_same_session_twice_is_not_recomputed(
    client: httpx.AsyncClient,
    runbody_service: RunbodyService,
    fake_analyzer: FakeAnalyzer,
    second_run_body: dict[str, Any],
) -> None:
    await _analyze(client, second_run_body)
    await runbody_service.wait(SECOND)

    again = await _analyze(client, second_run_body)

    assert again.status_code == 202
    assert again.json()["status"] == "done"
    assert fake_analyzer.calls == [SECOND]


async def test_same_session_id_from_other_user_is_conflict(
    client: httpx.AsyncClient, runbody_service: RunbodyService, second_run_body: dict[str, Any]
) -> None:
    await _analyze(client, second_run_body)
    await runbody_service.wait(SECOND)

    response = await _analyze(client, {**second_run_body, "userId": "u_other"})

    assert response.status_code == 409
    assert response.json() == {"reason": "SESSION_CONFLICT"}


async def test_pending_result_is_409(
    client: httpx.AsyncClient, fake_analyzer: FakeAnalyzer, second_run_body: dict[str, Any]
) -> None:
    fake_analyzer.gate = asyncio.Event()

    await _analyze(client, second_run_body)
    status = await client.get(f"/sessions/{SECOND}/status")
    result = await client.get(f"/sessions/{SECOND}")

    assert status.json()["status"] == "pending"
    assert result.status_code == 409
    assert result.json() == {"reason": "ANALYSIS_PENDING"}


async def test_engine_error_marks_failed_and_retry_recomputes(
    client: httpx.AsyncClient,
    runbody_service: RunbodyService,
    fake_analyzer: FakeAnalyzer,
    second_run_body: dict[str, Any],
) -> None:
    fake_analyzer.error = ValueError("boom")
    await _analyze(client, second_run_body)
    await runbody_service.wait(SECOND)

    status = (await client.get(f"/sessions/{SECOND}/status")).json()
    result = await client.get(f"/sessions/{SECOND}")

    assert (status["status"], status["errorCode"]) == ("failed", "ENGINE_ERROR")
    assert result.status_code == 409
    assert result.json() == {"reason": "ANALYSIS_FAILED"}

    fake_analyzer.error = None
    retry = await _analyze(client, second_run_body)
    await runbody_service.wait(SECOND)

    assert retry.json()["status"] == "pending"
    assert (await client.get(f"/sessions/{SECOND}")).status_code == 200
    assert fake_analyzer.calls == [SECOND, SECOND]


async def test_unknown_session_is_404(client: httpx.AsyncClient) -> None:
    status = await client.get("/sessions/nope/status")
    result = await client.get("/sessions/nope")

    assert (status.status_code, status.json()) == (404, {"reason": "SESSION_NOT_FOUND"})
    assert (result.status_code, result.json()) == (404, {"reason": "SESSION_NOT_FOUND"})


async def test_invalid_bodies_are_422(client: httpx.AsyncClient, second_run_body: dict[str, Any]) -> None:
    cases: list[dict[str, Any]] = [
        {**second_run_body, "samples": []},
        {**second_run_body, "activeMs": 0},
        {**second_run_body, "distanceMeters": 0},
        {**second_run_body, "email": "a@b.c"},  # 정의 밖 필드(회원 식별 항목)
        {k: v for k, v in second_run_body.items() if k != "profile"},
        {**second_run_body, "startedAt": "2026-10-19T19:09:39"},  # 시간대 없음
    ]
    for body in cases:
        response = await _analyze(client, body, session_id=SECOND)

        assert response.status_code == 422
        assert response.json() == {"reason": "INVALID_REQUEST"}


async def test_path_and_body_session_id_must_match(client: httpx.AsyncClient, second_run_body: dict[str, Any]) -> None:
    response = await _analyze(client, second_run_body, session_id="run-other")

    assert response.status_code == 422
    assert response.json() == {"reason": "INVALID_REQUEST"}


async def test_non_json_body_is_422(client: httpx.AsyncClient) -> None:
    response = await client.post(f"/sessions/{SECOND}/analyze", content=b"a=b", headers={"Content-Type": "text/plain"})

    assert response.status_code == 422
    assert response.json() == {"reason": "INVALID_REQUEST"}


async def test_broken_json_is_422(client: httpx.AsyncClient) -> None:
    response = await client.post(
        f"/sessions/{SECOND}/analyze", content=b"{", headers={"Content-Type": "application/json"}
    )

    assert response.status_code == 422


async def test_declared_too_large_is_413(client: httpx.AsyncClient) -> None:
    response = await client.post(
        f"/sessions/{SECOND}/analyze",
        content=b"{}",
        headers={"Content-Type": "application/json", "Content-Length": str(9 * 1024 * 1024)},
    )

    assert response.status_code == 413
    assert response.json() == {"reason": "TOO_LARGE"}


async def test_streamed_too_large_is_413(client: httpx.AsyncClient) -> None:
    """Content-Length 없이(chunked) 상한을 넘어도 끝까지 읽지 않고 413."""

    async def chunks() -> AsyncIterator[bytes]:
        for _ in range(9):
            yield b" " * (1024 * 1024)

    response = await client.post(
        f"/sessions/{SECOND}/analyze", content=chunks(), headers={"Content-Type": "application/json"}
    )

    assert response.status_code == 413


async def test_invalid_path_id_is_422(client: httpx.AsyncClient) -> None:
    response = await client.get("/sessions/" + "x" * 65)

    assert response.status_code == 422
    assert response.json() == {"reason": "INVALID_REQUEST"}


async def test_requires_internal_token(anon_client: httpx.AsyncClient, second_run_body: dict[str, Any]) -> None:
    response = await _analyze(anon_client, second_run_body)

    assert response.status_code == 401
    assert response.json() == {"reason": "UNAUTHORIZED"}
