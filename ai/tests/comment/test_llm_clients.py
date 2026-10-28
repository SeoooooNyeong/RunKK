"""ClaudeClient·OpenAiClient: SDK 설정(재시도 0, 시간 제한)과 응답 매핑. 실제 API는 부르지 않는다."""

from __future__ import annotations

from types import SimpleNamespace
from typing import Any

import anthropic
import httpx2
import openai
import pytest

from runkk_ai.comment import ClaudeClient, LlmClient, OpenAiClient


class _Recorder:
    def __init__(self, result: Any = None, error: BaseException | None = None) -> None:
        self.calls: list[dict[str, Any]] = []
        self._result = result
        self._error = error

    async def create(self, **kwargs: Any) -> Any:
        self.calls.append(kwargs)
        if self._error is not None:
            raise self._error
        return self._result


def _claude_message(text: str = "좋아요", stop_reason: str = "end_turn") -> Any:
    return SimpleNamespace(
        content=[SimpleNamespace(type="text", text=text)],
        stop_reason=stop_reason,
        usage=SimpleNamespace(input_tokens=50, output_tokens=12),
    )


def _openai_completion(text: str | None = "좋아요", finish_reason: str = "stop") -> Any:
    return SimpleNamespace(
        choices=[SimpleNamespace(message=SimpleNamespace(content=text), finish_reason=finish_reason)],
        usage=SimpleNamespace(prompt_tokens=40, completion_tokens=9),
    )


def test_clients_implement_one_abc() -> None:
    assert ClaudeClient.__bases__ == (LlmClient,)
    assert OpenAiClient.__bases__ == (LlmClient,)


def test_claude_sdk_has_no_retry_and_short_timeout() -> None:
    client = ClaudeClient(api_key="sk-test", model="claude-haiku-5-5", max_tokens=200, timeout_s=2.5)

    sdk = client._client
    assert isinstance(sdk, anthropic.AsyncAnthropic)
    assert sdk.max_retries == 0
    assert sdk.timeout.read == 2.5
    assert sdk.timeout.connect == 0.5
    assert client.model == "claude-haiku-5-5"


async def test_claude_complete_maps_reply() -> None:
    client = ClaudeClient(api_key="sk-test", model="claude-haiku-5-5", max_tokens=200, timeout_s=2.5)
    recorder = _Recorder(result=_claude_message("멋져요", "end_turn"))
    client._client = SimpleNamespace(messages=recorder)  # type: ignore[assignment]

    reply = await client.complete("프롬프트")

    assert (reply.text, reply.stop_reason, reply.input_tokens, reply.output_tokens) == ("멋져요", "end_turn", 50, 12)
    call = recorder.calls[0]
    assert call["model"] == "claude-haiku-5-5"
    assert call["max_tokens"] == 200
    assert call["messages"] == [{"role": "user", "content": "프롬프트"}]
    assert call["thinking"] == {"type": "disabled"}


async def test_claude_timeout_becomes_timeout_error() -> None:
    client = ClaudeClient(api_key="sk-test", model="m", max_tokens=200, timeout_s=2.5)
    request = httpx2.Request("POST", "https://api.anthropic.com/v1/messages")
    client._client = SimpleNamespace(messages=_Recorder(error=anthropic.APITimeoutError(request=request)))  # type: ignore[assignment]

    with pytest.raises(TimeoutError):
        await client.complete("p")


async def test_claude_without_key_fails_on_call_not_on_start() -> None:
    client = ClaudeClient(api_key=None, model="m", max_tokens=200, timeout_s=2.5)

    with pytest.raises(RuntimeError):
        await client.complete("p")
    await client.aclose()


def test_openai_sdk_has_no_retry_and_short_timeout() -> None:
    client = OpenAiClient(api_key="sk-test", model="gpt-x-mini", max_tokens=200, timeout_s=2.5)

    sdk = client._client
    assert isinstance(sdk, openai.AsyncOpenAI)
    assert sdk.max_retries == 0
    assert sdk.timeout.read == 2.5
    assert sdk.timeout.connect == 0.5


@pytest.mark.parametrize(
    ("finish_reason", "expected"),
    [("stop", "end_turn"), ("length", "max_tokens"), ("content_filter", "content_filter")],
)
async def test_openai_finish_reason_is_normalized(finish_reason: str, expected: str) -> None:
    client = OpenAiClient(api_key="sk-test", model="gpt-x-mini", max_tokens=200, timeout_s=2.5)
    recorder = _Recorder(result=_openai_completion("좋아요", finish_reason))
    client._client = SimpleNamespace(chat=SimpleNamespace(completions=recorder))  # type: ignore[assignment]

    reply = await client.complete("프롬프트")

    assert reply.stop_reason == expected
    assert (reply.text, reply.input_tokens, reply.output_tokens) == ("좋아요", 40, 9)
    assert recorder.calls[0]["max_completion_tokens"] == 200
    assert recorder.calls[0]["messages"] == [{"role": "user", "content": "프롬프트"}]


async def test_openai_empty_content_is_empty_text() -> None:
    client = OpenAiClient(api_key="sk-test", model="m", max_tokens=200, timeout_s=2.5)
    client._client = SimpleNamespace(  # type: ignore[assignment]
        chat=SimpleNamespace(completions=_Recorder(result=_openai_completion(None)))
    )

    reply = await client.complete("p")

    assert reply.text == ""


async def test_openai_timeout_becomes_timeout_error() -> None:
    client = OpenAiClient(api_key="sk-test", model="m", max_tokens=200, timeout_s=2.5)
    request = httpx2.Request("POST", "https://api.openai.com/v1/chat/completions")
    client._client = SimpleNamespace(  # type: ignore[assignment]
        chat=SimpleNamespace(completions=_Recorder(error=openai.APITimeoutError(request=request)))
    )

    with pytest.raises(TimeoutError):
        await client.complete("p")


async def test_openai_without_key_fails_on_call_not_on_start() -> None:
    client = OpenAiClient(api_key=None, model="m", max_tokens=200, timeout_s=2.5)

    with pytest.raises(RuntimeError):
        await client.complete("p")
    await client.aclose()


async def test_aclose_closes_sdk_clients() -> None:
    claude = ClaudeClient(api_key="sk-test", model="m", max_tokens=200, timeout_s=2.5)
    gpt = OpenAiClient(api_key="sk-test", model="m", max_tokens=200, timeout_s=2.5)

    await claude.aclose()
    await gpt.aclose()

    assert claude._client is not None
    assert claude._client.is_closed()
    assert gpt._client is not None
    assert gpt._client.is_closed()
