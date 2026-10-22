"""CommentRouter: 200·503·504·422, 2.7초 시간 제한, 출력 검증(14장 단위(ai 서버), D-58).

평가 기준(eval-harness, 코드 채점기):
- 정상 응답(stop_reason=end_turn, 1~120자) → 200 CommentOut
- 빈 값·공백만·120자 초과·비정상 종료(max_tokens·refusal·None) → 503 LLM_UNAVAILABLE
- LLM 오류 → 503 LLM_UNAVAILABLE, 시간 초과 → 504 LLM_TIMEOUT
- 정의 밖 항목(회원 식별자 등)·범위 밖 값 → 422, 응답에 입력값을 되돌려 주지 않는다
- 라우터 전체 2.7초 제한
"""

from __future__ import annotations

import json
import logging
import time
from collections.abc import AsyncIterator

import httpx
import pytest

from runkk_ai.comment import router as comment_router
from tests.conftest import VALID_SUMMARY, FakeLlmClient


async def test_returns_comment_text(client: httpx.AsyncClient, fake_llm: FakeLlmClient) -> None:
    response = await client.post("/comments", json=VALID_SUMMARY)

    assert response.status_code == 200
    assert response.json() == {"text": fake_llm.text}


async def test_text_is_trimmed(client: httpx.AsyncClient, fake_llm: FakeLlmClient) -> None:
    fake_llm.text = "  잘하고 있어요!\n"

    response = await client.post("/comments", json=VALID_SUMMARY)

    assert response.json() == {"text": "잘하고 있어요!"}


async def test_exactly_120_chars_is_accepted(client: httpx.AsyncClient, fake_llm: FakeLlmClient) -> None:
    fake_llm.text = "가" * 120

    response = await client.post("/comments", json=VALID_SUMMARY)

    assert response.status_code == 200
    assert len(response.json()["text"]) == 120


@pytest.mark.parametrize(
    ("text", "stop_reason"),
    [
        ("", "end_turn"),
        ("   \n", "end_turn"),
        ("가" * 121, "end_turn"),
        ("중간에 잘린 문장", "max_tokens"),
        ("거절", "refusal"),
        ("이유 없음", None),
    ],
    ids=["empty", "blank", "over-120", "max-tokens", "refusal", "no-stop-reason"],
)
async def test_invalid_output_is_503(
    client: httpx.AsyncClient, fake_llm: FakeLlmClient, text: str, stop_reason: str | None
) -> None:
    fake_llm.text = text
    fake_llm.stop_reason = stop_reason

    response = await client.post("/comments", json=VALID_SUMMARY)

    assert response.status_code == 503
    assert response.json() == {"reason": "LLM_UNAVAILABLE"}


async def test_llm_error_is_503(client: httpx.AsyncClient, fake_llm: FakeLlmClient) -> None:
    fake_llm.error = RuntimeError("provider 500")

    response = await client.post("/comments", json=VALID_SUMMARY)

    assert response.status_code == 503
    assert response.json() == {"reason": "LLM_UNAVAILABLE"}


async def test_llm_sdk_timeout_is_504(client: httpx.AsyncClient, fake_llm: FakeLlmClient) -> None:
    fake_llm.error = TimeoutError()

    response = await client.post("/comments", json=VALID_SUMMARY)

    assert response.status_code == 504
    assert response.json() == {"reason": "LLM_TIMEOUT"}


async def test_route_timeout_is_504(
    client: httpx.AsyncClient, fake_llm: FakeLlmClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(comment_router, "COMMENT_TIMEOUT_S", 0.05)
    fake_llm.delay_s = 1.0

    response = await client.post("/comments", json=VALID_SUMMARY)

    assert response.status_code == 504
    assert response.json() == {"reason": "LLM_TIMEOUT"}


def test_route_timeout_is_2_7_seconds() -> None:
    assert comment_router.COMMENT_TIMEOUT_S == 2.7


async def test_slow_llm_is_cut_at_2_7_seconds(client: httpx.AsyncClient, fake_llm: FakeLlmClient) -> None:
    """실제 시간으로 2.7초 제한을 확인한다(api 읽기 제한 3초 안에 답한다)."""
    fake_llm.delay_s = 10.0
    started = time.perf_counter()

    response = await client.post("/comments", json=VALID_SUMMARY)

    elapsed = time.perf_counter() - started
    assert response.status_code == 504
    assert 2.6 <= elapsed < 3.0


@pytest.mark.parametrize(
    "extra",
    [{"memberId": 7}, {"nickname": "러너"}, {"fromRun": 1, "toRun": 10}, {"route": [[37.5, 127.0]]}],
    ids=["memberId", "nickname", "run-sequence", "route"],
)
async def test_fields_outside_definition_are_422(
    client: httpx.AsyncClient, fake_llm: FakeLlmClient, extra: dict[str, object]
) -> None:
    response = await client.post("/comments", json={**VALID_SUMMARY, **extra})

    assert response.status_code == 422
    assert response.json() == {"reason": "INVALID_REQUEST"}
    assert fake_llm.prompts == []  # LLM에 보내지 않는다


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("frequentHourBand", 8),
        ("frequentHourBand", -1),
        ("frequentDay", "FUNDAY"),
        ("runCount", 0),
        ("runCount", 11),
        ("averageScore", -1),
        ("averageScore", "높음"),
        ("paceChangeSeconds", 1.5),
    ],
)
async def test_out_of_range_values_are_422(client: httpx.AsyncClient, field: str, value: object) -> None:
    response = await client.post("/comments", json={**VALID_SUMMARY, field: value})

    assert response.status_code == 422


async def test_missing_field_is_422(client: httpx.AsyncClient) -> None:
    body = {k: v for k, v in VALID_SUMMARY.items() if k != "runCount"}

    response = await client.post("/comments", json=body)

    assert response.status_code == 422


async def test_422_does_not_echo_input(client: httpx.AsyncClient) -> None:
    response = await client.post("/comments", json={**VALID_SUMMARY, "nickname": "비밀닉네임"})

    assert "비밀닉네임" not in response.text
    assert "nickname" not in response.text


@pytest.mark.parametrize("literal", ["Infinity", "-Infinity", "NaN"])
async def test_non_finite_average_score_is_422(
    client: httpx.AsyncClient, fake_llm: FakeLlmClient, literal: str
) -> None:
    """Python json은 Infinity·NaN을 받으므로 스키마가 막아야 프롬프트에 "inf"가 들어가지 않는다(L14)."""
    body = json.dumps({**VALID_SUMMARY, "averageScore": 0.0}).replace("0.0", literal)

    response = await client.post("/comments", content=body.encode(), headers={"Content-Type": "application/json"})

    assert response.status_code == 422
    assert fake_llm.prompts == []


async def test_route_timeout_is_logged_by_router(
    client: httpx.AsyncClient,
    fake_llm: FakeLlmClient,
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    """라우터 2.7초 제한으로 끊긴 호출은 라우터가 시간 초과로 센다(L9). 서비스는 취소로만 남긴다."""
    caplog.set_level(logging.INFO, logger="runkk_ai.comment")
    monkeypatch.setattr(comment_router, "COMMENT_TIMEOUT_S", 0.05)
    fake_llm.delay_s = 1.0

    await client.post("/comments", json=VALID_SUMMARY)

    assert "comment_route result=timeout" in caplog.text
    assert "result=cancelled" in caplog.text


async def test_non_json_body_is_422(client: httpx.AsyncClient) -> None:
    response = await client.post("/comments", content=b"not json", headers={"Content-Type": "application/json"})

    assert response.status_code == 422


async def test_declared_length_over_limit_is_413_without_calling_llm(
    client: httpx.AsyncClient, fake_llm: FakeLlmClient
) -> None:
    """본문 상한(N5): Content-Length가 상한을 넘으면 읽지 않고 413 TOO_LARGE, LLM에 보내지 않는다."""
    body = json.dumps({**VALID_SUMMARY, "padding": "x" * comment_router.COMMENT_BODY_MAX_BYTES}).encode()

    response = await client.post("/comments", content=body, headers={"Content-Type": "application/json"})

    assert response.status_code == 413
    assert response.json() == {"reason": "TOO_LARGE"}
    assert fake_llm.prompts == []


async def test_streamed_body_over_limit_is_413(client: httpx.AsyncClient, fake_llm: FakeLlmClient) -> None:
    """Content-Length 없이(chunked) 보내도 상한을 넘는 순간 끊는다."""

    async def chunks() -> AsyncIterator[bytes]:
        for _ in range(comment_router.COMMENT_BODY_MAX_BYTES // 512 + 2):
            yield b" " * 512

    response = await client.post("/comments", content=chunks(), headers={"Content-Type": "application/json"})

    assert response.status_code == 413
    assert response.json() == {"reason": "TOO_LARGE"}
    assert fake_llm.prompts == []


async def test_body_at_limit_is_accepted(client: httpx.AsyncClient, fake_llm: FakeLlmClient) -> None:
    """상한과 같은 크기는 받는다(JSON 뒤 공백으로 채운다)."""
    body = json.dumps(VALID_SUMMARY).encode()
    body += b" " * (comment_router.COMMENT_BODY_MAX_BYTES - len(body))

    response = await client.post("/comments", content=body, headers={"Content-Type": "application/json"})

    assert response.status_code == 200
    assert response.json() == {"text": fake_llm.text}


def test_body_limit_is_4_kib() -> None:
    assert comment_router.COMMENT_BODY_MAX_BYTES == 4096


async def test_non_json_content_type_is_422(client: httpx.AsyncClient, fake_llm: FakeLlmClient) -> None:
    response = await client.post(
        "/comments", content=json.dumps(VALID_SUMMARY).encode(), headers={"Content-Type": "text/plain"}
    )

    assert response.status_code == 422
    assert response.json() == {"reason": "INVALID_REQUEST"}
    assert fake_llm.prompts == []
