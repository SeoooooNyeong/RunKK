"""LLM 코멘트 생성 조정(6.1.16, D-58, D-62).

동시 호출 4개, 프로세스 하루 상한(daily_cap), 출력 검증을 맡는다. 재시도하지 않는다.
호출마다 모델·토큰 수·지연·결과를 남기고 프롬프트·응답 본문은 남기지 않는다.
"""

from __future__ import annotations

import asyncio
import logging
import time
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from typing import Literal

from runkk_ai.comment.llm import NORMAL_STOP, LlmClient, LlmReply
from runkk_ai.comment.prompt import PromptBuilder
from runkk_ai.comment.schemas import COMMENT_MAX_CHARS, SummaryIn

logger = logging.getLogger(__name__)

MAX_CONCURRENT_CALLS = 4
_KST = timezone(timedelta(hours=9))  # 한국은 일광 절약 시간이 없어 고정 오프셋으로 충분하다


def _today() -> date:
    return datetime.now(_KST).date()


@dataclass(frozen=True, slots=True)
class CommentResult:
    outcome: Literal["ok", "timeout", "error"]
    text: str | None = None


class CommentService:
    def __init__(self, llm: LlmClient, daily_cap: int) -> None:
        self._llm = llm
        self._daily_cap = daily_cap
        self._prompt_builder = PromptBuilder()
        self._semaphore = asyncio.Semaphore(MAX_CONCURRENT_CALLS)
        self._day: date | None = None
        self._calls_today = 0

    async def generate(self, summary: SummaryIn) -> CommentResult:
        async with self._semaphore:
            if not self._reserve_daily_call():
                self._log("error", 0.0, None, detail="daily_cap")
                return CommentResult("error")
            prompt = self._prompt_builder.build(summary)
            started = time.perf_counter()
            try:
                reply = await self._llm.complete(prompt)
            except asyncio.CancelledError:
                # 라우터 2.7초 제한(asyncio.timeout)이 이 호출을 끊었다
                self._log("timeout", started, None, detail="route_timeout")
                raise
            except TimeoutError:
                self._log("timeout", started, None)
                return CommentResult("timeout")
            except Exception as exc:
                self._log("error", started, None, detail=type(exc).__name__)
                return CommentResult("error")

        text = reply.text.strip()
        if not text or len(text) > COMMENT_MAX_CHARS or reply.stop_reason != NORMAL_STOP:
            self._log("error", started, reply, detail="invalid_output")
            return CommentResult("error")
        self._log("ok", started, reply)
        return CommentResult("ok", text)

    def _reserve_daily_call(self) -> bool:
        """상한 검사와 증가 사이에 await가 없어 동시 요청에도 상한을 넘지 않는다. 실패한 호출도 센다."""
        today = _today()
        if self._day != today:
            self._day = today
            self._calls_today = 0
        if self._calls_today >= self._daily_cap:
            return False
        self._calls_today += 1
        return True

    def _log(self, result: str, started: float, reply: LlmReply | None, detail: str = "") -> None:
        latency_ms = int((time.perf_counter() - started) * 1000) if started else 0
        logger.info(
            "llm_call model=%s input_tokens=%d output_tokens=%d latency_ms=%d result=%s detail=%s calls_today=%d",
            self._llm.model,
            reply.input_tokens if reply else 0,
            reply.output_tokens if reply else 0,
            latency_ms,
            result,
            detail,
            self._calls_today,
        )
