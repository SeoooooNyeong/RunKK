"""LlamaCppClient: 학습 때와 같은 대화 모양(빈 think 블록)과 JSON 강제로 llama.cpp /completion을 부른다."""

from __future__ import annotations

import json
from typing import Any

import httpx
import pytest

from runkk_ai.writer.checks import KEYS, MAX_LEN
from runkk_ai.writer.client import LlamaCppClient, chat_prompt


def test_chat_prompt_ends_with_empty_think_block() -> None:
    """학습 텍스트는 assistant 뒤에 빈 think 블록이 있었다. 추론도 같은 모양으로 열어야 JSON부터 쓴다."""
    prompt = chat_prompt("지시문", "분석 요약:\n{}")

    assert prompt.startswith("<|im_start|>system\n지시문<|im_end|>\n<|im_start|>user\n분석 요약:\n{}<|im_end|>\n")
    assert prompt.endswith("<|im_start|>assistant\n<think>\n\n</think>\n\n")


async def test_complete_sends_schema_and_returns_content() -> None:
    seen: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["path"] = request.url.path
        seen["body"] = json.loads(request.content)
        return httpx.Response(200, json={"content": '{"headline": "좋아요"}', "timings": {}})

    client = LlamaCppClient("http://writer:8080", temperature=0.5, transport=httpx.MockTransport(handler))
    raw = await client.complete("지시문", "요약")
    await client.aclose()

    body = seen["body"]
    assert seen["path"] == "/completion"
    assert raw == '{"headline": "좋아요"}'
    assert body["prompt"] == chat_prompt("지시문", "요약")
    assert body["temperature"] == 0.5
    assert body["cache_prompt"] is True
    schema = body["json_schema"]
    assert schema["required"] == list(KEYS)
    assert schema["additionalProperties"] is False
    assert {k: v["maxLength"] for k, v in schema["properties"].items()} == MAX_LEN


async def test_server_error_raises() -> None:
    client = LlamaCppClient(
        "http://writer:8080", temperature=0.5, transport=httpx.MockTransport(lambda _: httpx.Response(503))
    )

    with pytest.raises(httpx.HTTPStatusError):
        await client.complete("지시문", "요약")
    await client.aclose()
