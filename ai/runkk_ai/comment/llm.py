"""LLM 호출 계약과 제공자 구현(6.1.16, D-58).

SDK는 비동기·max_retries=0·timeout 2.5초(연결 0.5초)로 만든다. 재시도·대체는 api 쪽이 한다.
생성자는 값만 받고 Settings를 import하지 않는다(D-60).
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass

import anthropic
import openai

_CONNECT_TIMEOUT_S = 0.5
NORMAL_STOP = "end_turn"
_OPENAI_STOP_REASONS = {"stop": NORMAL_STOP, "length": "max_tokens"}


@dataclass(frozen=True, slots=True)
class LlmReply:
    text: str
    stop_reason: str | None  # 정상 종료는 "end_turn"으로 맞춘다
    input_tokens: int
    output_tokens: int


class LlmClient(ABC):
    def __init__(self, model: str) -> None:
        self._model = model

    @property
    def model(self) -> str:
        return self._model

    @abstractmethod
    async def complete(self, prompt: str) -> LlmReply:
        """시간 초과는 TimeoutError, 그 밖의 실패는 예외로 알린다."""

    async def aclose(self) -> None:
        return None


class ClaudeClient(LlmClient):
    def __init__(self, api_key: str | None, model: str, max_tokens: int, timeout_s: float) -> None:
        super().__init__(model)
        self._max_tokens = max_tokens
        # 키가 없으면 SDK를 만들지 않는다(로컬 프로필 등 다른 자격 증명으로 새지 않게).
        # 호출은 실패하고 api가 규칙 문구로 대체한다
        self._client: anthropic.AsyncAnthropic | None = (
            anthropic.AsyncAnthropic(
                api_key=api_key,
                max_retries=0,
                timeout=anthropic.Timeout(timeout_s, connect=_CONNECT_TIMEOUT_S),
            )
            if api_key
            else None
        )

    async def complete(self, prompt: str) -> LlmReply:
        if self._client is None:
            raise RuntimeError("LLM 제공자 키가 설정되지 않았다")
        try:
            message = await self._client.messages.create(
                model=self._model,
                max_tokens=self._max_tokens,
                messages=[{"role": "user", "content": prompt}],
                thinking={"type": "disabled"},  # 2.5초 안에 짧은 문장을 받기 위해 확장 추론을 끈다(제안, C-006)
            )
        except anthropic.APITimeoutError as exc:
            raise TimeoutError from exc
        text = "".join(block.text for block in message.content if block.type == "text")
        return LlmReply(
            text=text,
            stop_reason=message.stop_reason,
            input_tokens=message.usage.input_tokens,
            output_tokens=message.usage.output_tokens,
        )

    async def aclose(self) -> None:
        if self._client is not None:
            await self._client.close()


class OpenAiClient(LlmClient):
    def __init__(self, api_key: str | None, model: str, max_tokens: int, timeout_s: float) -> None:
        super().__init__(model)
        self._max_tokens = max_tokens
        self._client: openai.AsyncOpenAI | None = (
            openai.AsyncOpenAI(
                api_key=api_key,
                max_retries=0,
                timeout=openai.Timeout(timeout_s, connect=_CONNECT_TIMEOUT_S),
            )
            if api_key
            else None
        )

    async def complete(self, prompt: str) -> LlmReply:
        if self._client is None:
            raise RuntimeError("LLM 제공자 키가 설정되지 않았다")
        try:
            completion = await self._client.chat.completions.create(
                model=self._model,
                max_completion_tokens=self._max_tokens,
                messages=[{"role": "user", "content": prompt}],
            )
        except openai.APITimeoutError as exc:
            raise TimeoutError from exc
        choice = completion.choices[0]
        finish = choice.finish_reason
        usage = completion.usage
        return LlmReply(
            text=choice.message.content or "",
            stop_reason=_OPENAI_STOP_REASONS.get(finish, finish) if finish else None,
            input_tokens=usage.prompt_tokens if usage else 0,
            output_tokens=usage.completion_tokens if usage else 0,
        )

    async def aclose(self) -> None:
        if self._client is not None:
            await self._client.close()
