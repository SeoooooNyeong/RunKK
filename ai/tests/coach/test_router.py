"""POST /coach/chat: SSE 응답과 시작 전 거절(상태 코드)."""

from __future__ import annotations

from collections.abc import AsyncIterator
from datetime import UTC, datetime, timedelta

import httpx
import pytest
from fastapi import FastAPI

from runkk_ai.coach import (
    CHAT_BODY_MAX_BYTES,
    CoachMessage,
    CoachService,
    CoachTools,
    InMemoryCoachStore,
    ToolCall,
    get_coach_service,
)
from tests.coach.conftest import FakeCoachModel, Script, make_service

USER = "u_demo"
T0 = datetime(2026, 11, 12, 0, 0, tzinfo=UTC)


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


async def _client_with(app: FastAPI, service: CoachService) -> httpx.AsyncClient:
    app.dependency_overrides[get_coach_service] = lambda: service
    return httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://ai",
        headers={"X-Internal-Token": "test-internal-token-0123456789"},
    )


async def test_messages_returns_past_turns_oldest_first(app: FastAPI, tools: CoachTools) -> None:
    """이전 대화를 열면 지난 문답을 그대로 돌려준다(S15P31A207-300). 모델 키가 없어도 읽을 수 있다."""
    store = InMemoryCoachStore()
    for i, (role, text) in enumerate([("user", "어제 어땠어?"), ("assistant", "좋았어요."), ("user", "오늘은?")]):
        await store.append(CoachMessage("conv-1", USER, role, text, T0 + timedelta(seconds=i)))  # type: ignore[arg-type]

    async with await _client_with(app, make_service(tools, None, store)) as client:
        response = await client.get(f"/coach/users/{USER}/conversations/conv-1/messages")

    assert response.status_code == 200
    assert response.json() == {
        "conversationId": "conv-1",
        "messages": [
            {"role": "user", "content": "어제 어땠어?", "at": "2026-11-12T00:00:00Z"},
            {"role": "assistant", "content": "좋았어요.", "at": "2026-11-12T00:00:01Z"},
            {"role": "user", "content": "오늘은?", "at": "2026-11-12T00:00:02Z"},
        ],
    }


async def test_messages_of_unknown_conversation_is_empty(app: FastAPI, tools: CoachTools) -> None:
    async with await _client_with(app, make_service(tools, None)) as client:
        response = await client.get(f"/coach/users/{USER}/conversations/none/messages")

    assert response.status_code == 200
    assert response.json() == {"conversationId": "none", "messages": []}


async def test_messages_of_other_user_is_404(app: FastAPI, tools: CoachTools) -> None:
    store = InMemoryCoachStore()
    await store.append(CoachMessage("conv-1", "someone_else", "user", "남의 질문", T0))

    async with await _client_with(app, make_service(tools, None, store)) as client:
        response = await client.get(f"/coach/users/{USER}/conversations/conv-1/messages")

    assert response.status_code == 404
    assert response.json() == {"reason": "CONVERSATION_NOT_FOUND"}


async def test_messages_rejects_invalid_id_and_missing_token(
    app: FastAPI, tools: CoachTools, anon_client: httpx.AsyncClient
) -> None:
    async with await _client_with(app, make_service(tools, None)) as client:
        invalid = await client.get(f"/coach/users/{USER}/conversations/{'x' * 65}/messages")

    assert invalid.status_code == 422
    assert (await anon_client.get(f"/coach/users/{USER}/conversations/conv-1/messages")).status_code == 401
