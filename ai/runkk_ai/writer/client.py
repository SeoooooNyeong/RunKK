"""writer 모델 서버(llama.cpp server) 클라이언트(S15P31A207-196).

학습 데이터는 Qwen3 대화 템플릿으로 만들었고, 그 템플릿은 assistant 차례 앞에 빈 think 블록을 넣었다.
그래서 추론도 같은 모양(…assistant\\n<think>\\n\\n</think>\\n\\n)으로 열어 줘야 모델이 곧바로 JSON을 쓴다.
열지 않으면 모델이 think 블록부터 스스로 써서 앞에 특수 토큰이 붙는다(-194 평가에서 100건 모두).
출력은 llama.cpp 문법 제약(json_schema)으로 키 4개·길이 상한을 강제한다. 내용 검사는 서비스가 checks로 한다.
"""

from __future__ import annotations

from typing import Any, Protocol

import httpx

from runkk_ai.writer.checks import KEYS, MAX_LEN

MAX_NEW_TOKENS = 400  # 출력은 보통 200~250토큰. 길이 상한(항목당 120자)을 넘지 않는 여유

OUTPUT_SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {key: {"type": "string", "maxLength": MAX_LEN[key]} for key in KEYS},
    "required": list(KEYS),
    "additionalProperties": False,
}


def chat_prompt(system: str, user: str) -> str:
    """학습 때(tokenizer.apply_chat_template)와 같은 모양의 프롬프트. assistant 차례를 빈 think 블록까지 열어 둔다."""
    return (
        f"<|im_start|>system\n{system}<|im_end|>\n"
        f"<|im_start|>user\n{user}<|im_end|>\n"
        "<|im_start|>assistant\n<think>\n\n</think>\n\n"
    )


class CompletionClient(Protocol):
    async def complete(self, system: str, user: str) -> str: ...

    async def aclose(self) -> None: ...


class LlamaCppClient:
    """POST {base_url}/completion. 시간 제한은 서비스가 대기열까지 묶어서 건다(여기서는 연결이 끊긴 경우만 막는다)."""

    def __init__(
        self,
        base_url: str,
        *,
        temperature: float,
        max_tokens: int = MAX_NEW_TOKENS,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._temperature = temperature
        self._max_tokens = max_tokens
        self._http = httpx.AsyncClient(
            base_url=base_url, timeout=httpx.Timeout(300.0, connect=3.0), transport=transport
        )

    async def complete(self, system: str, user: str) -> str:
        response = await self._http.post(
            "/completion",
            json={
                "prompt": chat_prompt(system, user),
                "n_predict": self._max_tokens,
                "temperature": self._temperature,
                "json_schema": OUTPUT_SCHEMA,
                "cache_prompt": True,  # 지시문 부분은 매번 같아 다시 계산하지 않는다
            },
        )
        response.raise_for_status()
        content: str = response.json()["content"]
        return content

    async def aclose(self) -> None:
        await self._http.aclose()
