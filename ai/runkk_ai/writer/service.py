"""분석 결과의 템플릿 피드백을 writer 문장으로 다듬는다(S15P31A207-196·197).

- 쓸 모델은 등록부(registry)가 정한다. 켜진 모델이 없으면 writer를 부르지 않고 템플릿 문장을 그대로 둔다.
- 판단(ruleId·goodRuleId·moreFixes)과 다른 결과는 그대로 두고 headline·good·fix·next 네 문장만 바꾼다.
- 출력은 학습 데이터를 거른 것과 같은 checks로 검사한다(지어낸 숫자·금칙어·길이 등). 못 넘으면 한 번 더 쓰게 하고,
  그래도 못 넘으면 템플릿을 그대로 둔다.
- CPU 모델이라 한 번에 한 건만 보낸다. 앞 요청을 기다린 시간까지 합쳐 timeout_s를 넘기면 템플릿으로 끝낸다
  (분석 결과가 writer 때문에 오래 pending으로 남지 않게).
- 어떤 실패도 예외로 올리지 않는다. writer가 꺼져 있어도 분석은 템플릿으로 끝난다.
- 로그에는 세션 ID·결과·시도 횟수·지연·실패 사유만 남긴다(문장·요약 본문은 남기지 않는다).
"""

from __future__ import annotations

import asyncio
import logging
import time
from collections.abc import Callable

from runkk_ai.runbody import SessionResultOut
from runkk_ai.writer.brief import writer_brief
from runkk_ai.writer.checks import KEYS, check
from runkk_ai.writer.client import CompletionClient
from runkk_ai.writer.prompt import SYSTEM, user_message
from runkk_ai.writer.registry import ModelRegistry

logger = logging.getLogger(__name__)

MAX_ATTEMPTS = 2


class WriterService:
    def __init__(
        self,
        registry: ModelRegistry,
        *,
        client_for: Callable[[str], CompletionClient],
        timeout_s: float = 60.0,
    ) -> None:
        self._registry = registry
        self._client_for = client_for
        self._clients: dict[str, CompletionClient] = {}
        self._timeout_s = timeout_s
        self._turn = asyncio.Lock()

    def _client(self, base_url: str) -> CompletionClient:
        """주소마다 클라이언트 하나를 만들어 둔다(연결을 다시 맺지 않게)."""
        client = self._clients.get(base_url)
        if client is None:
            client = self._clients[base_url] = self._client_for(base_url)
        return client

    async def polish(self, result: SessionResultOut) -> SessionResultOut:
        started = time.perf_counter()
        attempts, reasons = 0, ""
        model = await self._registry.active()
        if model is None:
            return result
        client = self._client(model.base_url)
        try:
            brief = writer_brief(result)
            async with asyncio.timeout(self._timeout_s):
                async with self._turn:
                    for attempts in range(1, MAX_ATTEMPTS + 1):
                        checked = check(await client.complete(SYSTEM, user_message(brief)), brief)
                        if checked.ok and checked.output is not None:
                            self._log(result.sessionId, "writer", attempts, started)
                            feedback = result.feedback.model_copy(
                                update={
                                    **{key: checked.output[key] for key in KEYS},
                                    "source": "writer",
                                    "modelVersion": model.model_id,
                                }
                            )
                            return result.model_copy(update={"feedback": feedback})
                        reasons = ",".join(checked.reasons)
            self._log(result.sessionId, "check_failed", attempts, started, reasons)
        except TimeoutError:
            self._log(result.sessionId, "timeout", attempts, started)
        except Exception as exc:  # writer가 꺼져 있거나 응답이 깨져도 분석은 템플릿으로 끝낸다
            self._log(result.sessionId, "error", attempts, started, type(exc).__name__)
        return result

    async def aclose(self) -> None:
        for client in self._clients.values():
            await client.aclose()
        self._clients.clear()

    @staticmethod
    def _log(session_id: str, outcome: str, attempts: int, started: float, detail: str = "") -> None:
        logger.info(
            "writer_polish session_id=%s result=%s attempts=%d latency_ms=%d detail=%s",
            session_id,
            outcome,
            attempts,
            int((time.perf_counter() - started) * 1000),
            detail,
        )
