"""POST /coach/chat: SSE 응답과 시작 전 거절(상태 코드)."""

from __future__ import annotations

from collections.abc import AsyncIterator

import httpx
import pytest
from fastapi import FastAPI

from runkk_ai.coach import CHAT_BODY_MAX_BYTES, CoachTools, ToolCall, get_coach_service
from tests.coach.conftest import FakeCoachModel, Script, make_service

USER = "u_demo"


@pytest.fixture
async def coach_client(app: FastAPI, tools: CoachTools) -> AsyncIterator[httpx.AsyncClient]:
    model = FakeCoachModel(
        [Script(calls=[ToolCall("t1", "getMonthlyResult", {})]), Script(texts=["이번 달 ", "2회예요."])]
    )
    app.dependency_overrides[get_coach_service] = lambda: make_service(tools, model)
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(
        transport=transport, base_url="http://ai", headers={"X-Internal-Token": "test-internal-token-0123456789"}
    ) as client:
        yield client


def _events(text: str) -> list[tuple[str, str]]:
    out = []
    for block in text.strip().split("\n\n"):
        lines = dict(line.split(": ", 1) for line in block.splitlines())
        out.append((lines["event"], lines["data"]))
    return out


async def test_chat_streams_sse(coach_client: httpx.AsyncClient) -> None:
    response = await coach_client.post("/coach/chat", json={"userId": USER, "message": "이번 달 얼마나 뛰었어?"})

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    assert response.headers["x-accel-buffering"] == "no"
    events = _events(response.text)
    assert [e for e, _ in events] == ["meta", "tool", "delta", "delta", "done"]
    assert events[1][1] == '{"name": "getMonthlyResult"}'


@pytest.mark.parametrize(
    "body",
    [
        {"message": "userId 없음"},
        {"userId": USER, "message": ""},
        {"userId": USER, "message": "가" * 1001},
        {"userId": USER, "message": "x", "extra": 1},
    ],
)
async def test_invalid_body_is_422(coach_client: httpx.AsyncClient, body: dict[str, object]) -> None:
    response = await coach_client.post("/coach/chat", json=body)

    assert response.status_code == 422
    assert response.json() == {"reason": "INVALID_REQUEST"}


async def test_broken_json_is_422(coach_client: httpx.AsyncClient) -> None:
    response = await coach_client.post("/coach/chat", content=b"{broken", headers={"Content-Type": "application/json"})

    assert response.status_code == 422


async def test_too_large_is_413(coach_client: httpx.AsyncClient) -> None:
    response = await coach_client.post("/coach/chat", content=b"x" * (CHAT_BODY_MAX_BYTES + 1))

    assert response.status_code == 413
    assert response.json() == {"reason": "TOO_LARGE"}


async def test_rejected_before_stream_keeps_status(app: FastAPI, tools: CoachTools) -> None:
    app.dependency_overrides[get_coach_service] = lambda: make_service(tools, None)
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(
        transport=transport, base_url="http://ai", headers={"X-Internal-Token": "test-internal-token-0123456789"}
    ) as client:
        response = await client.post("/coach/chat", json={"userId": USER, "message": "안녕"})

    assert response.status_code == 503
    assert response.json() == {"reason": "LLM_UNAVAILABLE"}
