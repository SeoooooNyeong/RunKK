"""코치 대화 서비스: 도구 선택 → 실행 → 답 흘려보내기, 대화 이어 가기, 상한·실패 처리."""

from __future__ import annotations

import json

import pytest

from runkk_ai.coach import ChatEvent, ChatIn, ChatRejected, CoachTools, InMemoryCoachStore, ToolCall
from runkk_ai.coach.service import MAX_TOOL_ROUNDS
from tests.coach.conftest import FakeCoachModel, Script, make_service

USER = "u_demo"


async def _collect(service, chat: ChatIn) -> tuple[str, list[ChatEvent]]:  # type: ignore[no-untyped-def]
    conversation_id, events = await service.start(chat)
    return conversation_id, [e async for e in events]


async def test_tool_then_answer(tools: CoachTools) -> None:
    model = FakeCoachModel(
        [
            Script(texts=["기록을 볼게요. "], calls=[ToolCall("t1", "getSessionResult", {})]),
            Script(texts=["어제는 출발이 ", "빨랐어요."]),
        ]
    )
    store = InMemoryCoachStore()
    service = make_service(tools, model, store)

    conversation_id, events = await _collect(service, ChatIn(userId=USER, message="어제 러닝 어땠어?"))

    assert [e.event for e in events] == ["meta", "delta", "tool", "delta", "delta", "done"]
    assert events[0].data == {"conversationId": conversation_id}
    assert events[2].data == {"name": "getSessionResult"}
    assert events[-1].data == {
        "model": "fake-haiku",
        "inputTokens": 200,
        "outputTokens": 40,
        "tools": ["getSessionResult"],
    }
    # 두 번째 turn에 도구 결과(실측 10/19 요약)가 들어간다
    tool_result = model.seen[1]["messages"][-1]["content"][0]
    assert tool_result["tool_use_id"] == "t1"
    assert json.loads(tool_result["content"])["feedback"]["headline"] == "출발이 너무 빨랐어요"
    saved = await store.recent(conversation_id, 10)
    assert [(m.role, m.content) for m in saved] == [
        ("user", "어제 러닝 어땠어?"),
        ("assistant", "기록을 볼게요. 어제는 출발이 빨랐어요."),
    ]
    assert saved[1].tools == "getSessionResult"
    assert saved[1].input_tokens == 200


async def test_conversation_continues_with_history(tools: CoachTools) -> None:
    model = FakeCoachModel([Script(texts=["첫 답"]), Script(texts=["둘째 답"])])
    service = make_service(tools, model)
    conversation_id, _ = await _collect(service, ChatIn(userId=USER, message="안녕"))

    await _collect(service, ChatIn(userId=USER, conversationId=conversation_id, message="이어서"))

    assert [m["content"] for m in model.seen[1]["messages"]] == ["안녕", "첫 답", "이어서"]
    assert "오늘은 2026-10-20" in model.seen[1]["system"]


async def test_other_users_conversation_is_rejected(tools: CoachTools) -> None:
    service = make_service(tools, FakeCoachModel([Script(texts=["답"])]))
    conversation_id, _ = await _collect(service, ChatIn(userId=USER, message="안녕"))

    with pytest.raises(ChatRejected) as rejected:
        await service.start(ChatIn(userId="someone_else", conversationId=conversation_id, message="훔쳐보기"))
    assert (rejected.value.status, rejected.value.reason) == (404, "CONVERSATION_NOT_FOUND")


async def test_tool_rounds_are_capped(tools: CoachTools) -> None:
    loop = [Script(calls=[ToolCall(f"t{i}", "getBaseline", {})]) for i in range(10)]
    model = FakeCoachModel(loop)
    service = make_service(tools, model)

    _, events = await _collect(service, ChatIn(userId=USER, message="계속 도구만 불러"))

    assert [e.event for e in events].count("tool") == MAX_TOOL_ROUNDS
    assert [s["allow_tools"] for s in model.seen] == [True] * MAX_TOOL_ROUNDS + [False]
    assert events[-1].event == "done"


async def test_model_failure_becomes_error_event_and_no_answer_saved(tools: CoachTools) -> None:
    store = InMemoryCoachStore()
    service = make_service(tools, FakeCoachModel([], error=ConnectionError("down")), store)

    conversation_id, events = await _collect(service, ChatIn(userId=USER, message="안녕"))

    assert [e.event for e in events] == ["meta", "error"]
    assert events[-1].data == {"reason": "LLM_ERROR"}
    assert [m.role for m in await store.recent(conversation_id, 10)] == ["user"]


async def test_timeout_becomes_error_event(tools: CoachTools) -> None:
    service = make_service(tools, FakeCoachModel([], error=TimeoutError()))

    _, events = await _collect(service, ChatIn(userId=USER, message="안녕"))

    assert events[-1].data == {"reason": "LLM_TIMEOUT"}


async def test_no_key_means_unavailable(tools: CoachTools) -> None:
    with pytest.raises(ChatRejected) as rejected:
        await make_service(tools, None).start(ChatIn(userId=USER, message="안녕"))
    assert (rejected.value.status, rejected.value.reason) == (503, "LLM_UNAVAILABLE")


async def test_daily_caps(tools: CoachTools) -> None:
    service = make_service(tools, FakeCoachModel([]), daily_cap=3, user_daily_cap=2)
    for _ in range(2):
        await _collect(service, ChatIn(userId=USER, message="질문"))

    with pytest.raises(ChatRejected) as per_user:
        await service.start(ChatIn(userId=USER, message="세 번째"))
    await _collect(service, ChatIn(userId="u2", message="다른 사용자"))
    with pytest.raises(ChatRejected) as total:
        await service.start(ChatIn(userId="u3", message="전체 상한"))

    assert per_user.value.reason == total.value.reason == "QUOTA_EXCEEDED"
    assert per_user.value.status == 429


def test_sse_format() -> None:
    assert ChatEvent("delta", {"text": "안녕"}).sse() == 'event: delta\ndata: {"text": "안녕"}\n\n'
