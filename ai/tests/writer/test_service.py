"""WriterService: 템플릿 피드백을 writer 문장으로 바꾸되, 검사를 못 넘거나 느리거나 꺼져 있으면 템플릿을 그대로 둔다."""

from __future__ import annotations

import asyncio
import json
from typing import Any

import httpx
import pytest

from runkk_ai.runbody import SessionResultOut
from runkk_ai.writer.prompt import SYSTEM, user_message
from runkk_ai.writer.service import WriterService

GOOD = {
    "headline": "출발이 조금 빨랐어요",
    "good": "회복 심박이 지난번보다 좋아졌어요.",
    "fix": "초반을 조금 더 천천히 시작하면 후반이 편해져요.",
    "next": "다음에는 첫 1 km를 천천히 달려 보세요.",
}
BAD = {**GOOD, "fix": "심박 999까지 올라갔어요."}  # 요약에 없는 숫자


class FakeClient:
    def __init__(self, *replies: str | BaseException, delay: float = 0.0) -> None:
        self.replies = list(replies)
        self.delay = delay
        self.calls: list[tuple[str, str]] = []
        self.active = 0
        self.max_active = 0

    async def complete(self, system: str, user: str) -> str:
        self.calls.append((system, user))
        self.active += 1
        self.max_active = max(self.max_active, self.active)
        try:
            await asyncio.sleep(self.delay)
            reply = self.replies.pop(0) if len(self.replies) > 1 else self.replies[0]
            if isinstance(reply, BaseException):
                raise reply
            return reply
        finally:
            self.active -= 1

    async def aclose(self) -> None:
        return None


def _service(client: FakeClient, **kwargs: Any) -> WriterService:
    return WriterService(client, model_version="runkk-writer-v1", **kwargs)


async def test_rewrites_feedback_and_marks_source(second: SessionResultOut, brief: dict[str, Any]) -> None:
    client = FakeClient(json.dumps(GOOD, ensure_ascii=False))

    polished = await _service(client).polish(second)

    fb = polished.feedback
    assert (fb.headline, fb.good, fb.fix, fb.next) == tuple(GOOD.values())
    assert (fb.source, fb.modelVersion) == ("writer", "runkk-writer-v1")
    # 판단(규칙)과 나머지 결과는 그대로다
    assert (fb.ruleId, fb.goodRuleId, fb.moreFixes) == (
        second.feedback.ruleId,
        second.feedback.goodRuleId,
        second.feedback.moreFixes,
    )
    assert polished.model_dump(exclude={"feedback"}) == second.model_dump(exclude={"feedback"})
    assert client.calls == [(SYSTEM, user_message(brief))]


async def test_retries_once_after_failed_check(second: SessionResultOut) -> None:
    client = FakeClient(json.dumps(BAD, ensure_ascii=False), json.dumps(GOOD, ensure_ascii=False))

    polished = await _service(client).polish(second)

    assert polished.feedback.source == "writer"
    assert len(client.calls) == 2


async def test_keeps_template_when_both_attempts_fail(second: SessionResultOut) -> None:
    client = FakeClient("JSON 아님", json.dumps(BAD, ensure_ascii=False))

    polished = await _service(client).polish(second)

    assert polished is second
    assert len(client.calls) == 2


@pytest.mark.parametrize(
    "error",
    [httpx.ConnectError("writer 꺼짐"), httpx.HTTPStatusError("503", request=None, response=None), ValueError()],  # type: ignore[arg-type]
)
async def test_keeps_template_when_writer_errors(second: SessionResultOut, error: BaseException) -> None:
    polished = await _service(FakeClient(error)).polish(second)

    assert polished is second


async def test_keeps_template_when_too_slow(second: SessionResultOut) -> None:
    client = FakeClient(json.dumps(GOOD, ensure_ascii=False), delay=1.0)

    polished = await _service(client, timeout_s=0.05).polish(second)

    assert polished is second


async def test_calls_writer_one_at_a_time(second: SessionResultOut) -> None:
    """CPU 모델이라 동시에 여러 건을 넣으면 모두 느려진다. 한 번에 하나씩 보낸다."""
    client = FakeClient(json.dumps(GOOD, ensure_ascii=False), delay=0.01)
    service = _service(client)

    results = await asyncio.gather(*(service.polish(second) for _ in range(3)))

    assert all(r.feedback.source == "writer" for r in results)
    assert client.max_active == 1


async def test_queue_wait_counts_toward_timeout(second: SessionResultOut) -> None:
    """앞 요청을 기다린 시간도 제한에 들어간다(밀리면 기다리지 않고 템플릿으로 끝낸다)."""
    client = FakeClient(json.dumps(GOOD, ensure_ascii=False), delay=0.08)
    service = _service(client, timeout_s=0.1)

    first, second_result = await asyncio.gather(service.polish(second), service.polish(second))

    assert first.feedback.source == "writer"
    assert second_result is second
