"""ClaudeCoachModel: SDK 스트림의 글자 조각과 마지막 메시지(도구 선택·토큰)를 이벤트로 바꾼다(실제 API 호출 없음)."""

from __future__ import annotations

from types import SimpleNamespace
from typing import Any

from runkk_ai.coach import ClaudeCoachModel, ModelTurn, TextDelta, ToolCall


class _FakeStream:
    def __init__(self, texts: list[str], final: Any) -> None:
        self._texts, self._final = texts, final

    async def __aenter__(self) -> _FakeStream:
        return self

    async def __aexit__(self, *exc: object) -> None:
        return None

    def __aiter__(self) -> Any:
        async def gen() -> Any:
            yield SimpleNamespace(type="message_start")
            for t in self._texts:
                yield SimpleNamespace(type="text", text=t)

        return gen()

    async def get_final_message(self) -> Any:
        return self._final


async def test_stream_turns_sdk_events_into_coach_events() -> None:
    model = ClaudeCoachModel(api_key="k", model="claude-haiku-5-5", max_tokens=100, timeout_s=5)
    final = SimpleNamespace(
        content=[
            SimpleNamespace(type="text", text="볼게요"),
            SimpleNamespace(type="tool_use", id="t1", name="getBaseline", input={}),
        ],
        stop_reason="tool_use",
        usage=SimpleNamespace(input_tokens=321, output_tokens=12),
    )
    calls: list[dict[str, Any]] = []

    def fake_stream(**kwargs: Any) -> _FakeStream:
        calls.append(kwargs)
        return _FakeStream(["볼", "게요"], final)

    model._client.messages.stream = fake_stream  # type: ignore[method-assign]

    events = [
        e async for e in model.stream("sys", [{"role": "user", "content": "q"}], [{"name": "x"}], allow_tools=False)
    ]

    assert events[:2] == [TextDelta("볼"), TextDelta("게요")]
    assert events[2] == ModelTurn("볼게요", [ToolCall("t1", "getBaseline", {})], "tool_use", 321, 12)
    assert calls[0]["tool_choice"] == {"type": "none"}
    assert calls[0]["thinking"] == {"type": "disabled"}
    assert model.name == "claude-haiku-5-5"
    assert events[2].assistant_content()[1] == {"type": "tool_use", "id": "t1", "name": "getBaseline", "input": {}}
    await model.aclose()
