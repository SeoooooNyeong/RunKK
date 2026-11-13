"""코치 대화(F5, S15P31A207-193): 질문 → 모델이 도구 선택 → 도구 실행 → 모델 답을 글자 단위로 흘려보낸다.

이벤트(SSE로 나간다)
- meta  {"conversationId"}            맨 처음 한 번
- tool  {"name"}                      도구를 부를 때마다(앱이 "기록 확인 중"을 띄운다)
- delta {"text"}                      답 조각(도구를 부르기 전 혼잣말은 보내지 않는다)
- done  {"model", "inputTokens", "outputTokens", "tools"}  정상 끝
- error {"reason"}                    중간 실패(LLM_TIMEOUT, LLM_ERROR). 이미 보낸 delta는 버리게 한다

크레딧 보호: 프로세스 하루 상한(daily_cap)과 사용자별 하루 상한(user_daily_cap). 넘으면 시작 전에 거절한다.
로그에는 대화 ID·도구 이름·토큰 수·지연만 남기고 질문·답 내용은 남기지 않는다.
"""

from __future__ import annotations

import asyncio
import json
import logging
import time
import uuid
from collections import Counter
from collections.abc import AsyncIterator, Callable
from dataclasses import dataclass
from datetime import UTC, date, datetime, timedelta
from typing import Any, Literal

from runkk_ai.coach.model import CoachModel, ModelTurn, TextDelta
from runkk_ai.coach.prompt import system_prompt
from runkk_ai.coach.schemas import ChatIn
from runkk_ai.coach.store import CoachMessage, CoachMessageStore
from runkk_ai.coach.tools import TOOL_SPECS, CoachTools

logger = logging.getLogger(__name__)

HISTORY_MESSAGES = 10  # 이전 대화는 최근 5번 주고받은 것까지만 다시 보낸다(토큰 절약)
HISTORY_VIEW_MESSAGES = 200  # 지난 문답 조회는 최근 100번 주고받은 것까지만 돌려준다(응답 크기 상한)
MAX_TOOL_ROUNDS = 3  # 도구를 고르는 turn 상한. 넘으면 도구 없이 답하게 한다
_KST = timedelta(hours=9)

EventName = Literal["meta", "tool", "delta", "done", "error"]


@dataclass(frozen=True, slots=True)
class ChatEvent:
    event: EventName
    data: dict[str, Any]

    def sse(self) -> str:
        return f"event: {self.event}\ndata: {json.dumps(self.data, ensure_ascii=False)}\n\n"


class ChatRejected(Exception):
    """대화를 시작하지 않는다. reason은 {"reason"} 본문, status는 HTTP 상태."""

    def __init__(self, status: int, reason: str) -> None:
        super().__init__(reason)
        self.status = status
        self.reason = reason


def _now() -> datetime:
    return datetime.now(UTC)


def _kst_today(now: datetime) -> date:
    return (now.astimezone(UTC) + _KST).date()


class CoachService:
    def __init__(
        self,
        model: CoachModel | None,
        tools: CoachTools,
        store: CoachMessageStore,
        *,
        daily_cap: int,
        user_daily_cap: int,
        timeout_s: float,
        clock: Callable[[], datetime] = _now,
    ) -> None:
        self._model = model
        self._tools = tools
        self._store = store
        self._daily_cap = daily_cap
        self._user_daily_cap = user_daily_cap
        self._timeout_s = timeout_s
        self._clock = clock
        self._day: date | None = None
        self._used_total = 0
        self._used_by_user: Counter[str] = Counter()

    async def start(self, chat: ChatIn) -> tuple[str, AsyncIterator[ChatEvent]]:
        """시작 전에 거절할 것은 여기서 ChatRejected로 끊는다(응답 상태를 정해야 해서 스트림 전에 확인)."""
        if self._model is None:
            raise ChatRejected(503, "LLM_UNAVAILABLE")
        conversation_id = chat.conversationId or uuid.uuid4().hex
        if chat.conversationId is not None:
            owner = await self._store.owner(chat.conversationId)
            if owner is not None and owner != chat.userId:
                raise ChatRejected(404, "CONVERSATION_NOT_FOUND")
        if not self._reserve(chat.userId):
            raise ChatRejected(429, "QUOTA_EXCEEDED")
        return conversation_id, self._run(self._model, chat, conversation_id)

    async def history(self, user_id: str, conversation_id: str) -> list[CoachMessage]:
        """대화의 지난 문답을 오래된 순으로 돌려준다(S15P31A207-300). 앱이 이전 대화를 열 때 화면에 다시 그린다.

        다른 사용자의 대화면 ChatRejected(404)로 끊는다. 기록이 없는 대화(보관 기한으로 지워짐 등)는 빈 목록이다.
        모델이 없어도(키 없음) 저장된 기록은 읽을 수 있다.
        """
        owner = await self._store.owner(conversation_id)
        if owner is None:
            return []
        if owner != user_id:
            raise ChatRejected(404, "CONVERSATION_NOT_FOUND")
        return await self._store.recent(conversation_id, HISTORY_VIEW_MESSAGES)

    def _reserve(self, user_id: str) -> bool:
        today = _kst_today(self._clock())
        if today != self._day:
            self._day, self._used_total = today, 0
            self._used_by_user.clear()
        if self._used_total >= self._daily_cap or self._used_by_user[user_id] >= self._user_daily_cap:
            return False
        self._used_total += 1
        self._used_by_user[user_id] += 1
        return True

    async def _run(self, model: CoachModel, chat: ChatIn, conversation_id: str) -> AsyncIterator[ChatEvent]:
        started = time.perf_counter()
        history = await self._store.recent(conversation_id, HISTORY_MESSAGES)
        messages: list[dict[str, Any]] = [{"role": m.role, "content": m.content} for m in history]
        messages.append({"role": "user", "content": chat.message})
        await self._store.append(CoachMessage(conversation_id, chat.userId, "user", chat.message, self._clock()))
        yield ChatEvent("meta", {"conversationId": conversation_id})

        system = system_prompt(_kst_today(self._clock()))
        answer: list[str] = []
        used_tools: list[str] = []
        tokens_in = tokens_out = 0
        try:
            async with asyncio.timeout(self._timeout_s):
                for round_no in range(MAX_TOOL_ROUNDS + 1):
                    allow_tools = round_no < MAX_TOOL_ROUNDS
                    turn: ModelTurn | None = None
                    # 첫 turn은 도구를 부르기 전 혼잣말("기록을 확인해 볼게요…")이 섞이기 쉬워 끝날 때까지 모아 둔다.
                    # 도구를 골랐으면 버리고, 바로 답했으면 그때 보낸다. 도구 결과를 받은 뒤 turn은 바로 흘려보낸다
                    held: list[str] | None = [] if round_no == 0 else None
                    async for event in model.stream(system, messages, TOOL_SPECS, allow_tools=allow_tools):
                        if not isinstance(event, TextDelta):
                            turn = event
                        elif held is not None:
                            held.append(event.text)
                        else:
                            answer.append(event.text)
                            yield ChatEvent("delta", {"text": event.text})
                    if turn is None:
                        raise RuntimeError("모델이 turn을 끝내지 않았다")
                    tokens_in += turn.input_tokens
                    tokens_out += turn.output_tokens
                    if not turn.tool_calls or not allow_tools:
                        for text in held or []:
                            answer.append(text)
                            yield ChatEvent("delta", {"text": text})
                        break
                    messages.append({"role": "assistant", "content": turn.assistant_content()})
                    results = []
                    for call in turn.tool_calls:
                        used_tools.append(call.name)
                        yield ChatEvent("tool", {"name": call.name})
                        out = await self._tools.call(chat.userId, call.name, call.input)
                        results.append(
                            {
                                "type": "tool_result",
                                "tool_use_id": call.id,
                                "content": json.dumps(out, ensure_ascii=False),
                            }
                        )
                    messages.append({"role": "user", "content": results})
        except TimeoutError:
            self._log(conversation_id, "timeout", started, used_tools, tokens_in, tokens_out)
            yield ChatEvent("error", {"reason": "LLM_TIMEOUT"})
            return
        except Exception as exc:  # 모델 실패로 스트림을 끊지 않고 error 이벤트로 알린다
            self._log(conversation_id, "error", started, used_tools, tokens_in, tokens_out, type(exc).__name__)
            yield ChatEvent("error", {"reason": "LLM_ERROR"})
            return

        text = "".join(answer).strip()
        await self._store.append(
            CoachMessage(
                conversation_id,
                chat.userId,
                "assistant",
                text,
                self._clock(),
                model=model.name,
                input_tokens=tokens_in,
                output_tokens=tokens_out,
                tools=",".join(used_tools) or None,
            )
        )
        self._log(conversation_id, "ok", started, used_tools, tokens_in, tokens_out)
        yield ChatEvent(
            "done",
            {"model": model.name, "inputTokens": tokens_in, "outputTokens": tokens_out, "tools": used_tools},
        )

    @staticmethod
    def _log(
        conversation_id: str,
        result: str,
        started: float,
        tools: list[str],
        tokens_in: int,
        tokens_out: int,
        detail: str = "",
    ) -> None:
        logger.info(
            "coach_chat conversation_id=%s result=%s latency_ms=%d tools=%s input_tokens=%d output_tokens=%d detail=%s",
            conversation_id,
            result,
            int((time.perf_counter() - started) * 1000),
            ",".join(tools),
            tokens_in,
            tokens_out,
            detail,
        )
