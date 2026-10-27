"""코치 모델 호출(API 모델, 도구 사용 + 스트리밍). 서비스는 CoachModel 규약만 안다.

한 번의 호출(turn)마다 글자 조각(TextDelta)을 흘려보내고, 끝에 그 turn 전체(ModelTurn)를 한 번 낸다.
모델이 도구를 고르면 ModelTurn.tool_calls가 차 있고, 서비스가 도구를 실행해 다음 turn을 부른다.
"""

from __future__ import annotations

from collections.abc import AsyncIterator, Sequence
from dataclasses import dataclass, field
from typing import Any, Protocol

import anthropic

_CONNECT_TIMEOUT_S = 3.0


@dataclass(frozen=True, slots=True)
class TextDelta:
    text: str


@dataclass(frozen=True, slots=True)
class ToolCall:
    id: str
    name: str
    input: dict[str, Any]


@dataclass(frozen=True, slots=True)
class ModelTurn:
    text: str
    tool_calls: list[ToolCall] = field(default_factory=list)
    stop_reason: str | None = None
    input_tokens: int = 0
    output_tokens: int = 0

    def assistant_content(self) -> list[dict[str, Any]]:
        """다음 turn에 다시 보낼 assistant 메시지(Anthropic 형식)."""
        content: list[dict[str, Any]] = [{"type": "text", "text": self.text}] if self.text else []
        content += [{"type": "tool_use", "id": c.id, "name": c.name, "input": c.input} for c in self.tool_calls]
        return content


ModelEvent = TextDelta | ModelTurn


class CoachModel(Protocol):
    @property
    def name(self) -> str: ...

    def stream(
        self, system: str, messages: Sequence[dict[str, Any]], tools: Sequence[dict[str, Any]], *, allow_tools: bool
    ) -> AsyncIterator[ModelEvent]:
        """allow_tools=False면 도구 정의는 보내되 쓰지 못하게 한다(앞 turn의 도구 기록이 메시지에 남아 있어서)."""
        ...

    async def aclose(self) -> None: ...


class ClaudeCoachModel:
    """Anthropic Messages API(GMS 호환 주소는 SDK가 ANTHROPIC_BASE_URL로 읽는다)."""

    def __init__(self, api_key: str, model: str, max_tokens: int, timeout_s: float) -> None:
        self._model = model
        self._max_tokens = max_tokens
        self._client = anthropic.AsyncAnthropic(
            api_key=api_key, max_retries=1, timeout=anthropic.Timeout(timeout_s, connect=_CONNECT_TIMEOUT_S)
        )

    @property
    def name(self) -> str:
        return self._model

    async def stream(
        self, system: str, messages: Sequence[dict[str, Any]], tools: Sequence[dict[str, Any]], *, allow_tools: bool
    ) -> AsyncIterator[ModelEvent]:
        extra: dict[str, Any] = {"tools": list(tools), "tool_choice": {"type": "auto" if allow_tools else "none"}}
        async with self._client.messages.stream(
            model=self._model,
            max_tokens=self._max_tokens,
            system=system,
            messages=list(messages),  # type: ignore[arg-type]
            thinking={"type": "disabled"},
            **extra,
        ) as stream:
            async for event in stream:
                if event.type == "text":
                    yield TextDelta(event.text)
            final = await stream.get_final_message()
        yield ModelTurn(
            text="".join(b.text for b in final.content if b.type == "text"),
            tool_calls=[
                ToolCall(b.id, b.name, dict(b.input) if isinstance(b.input, dict) else {})
                for b in final.content
                if b.type == "tool_use"
            ],
            stop_reason=final.stop_reason,
            input_tokens=final.usage.input_tokens,
            output_tokens=final.usage.output_tokens,
        )

    async def aclose(self) -> None:
        await self._client.close()
