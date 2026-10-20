"""CommentRouter: 200·503·504·422, 2.7초 시간 제한, 출력 검증(14장 단위(ai 서버), D-58).

평가 기준(eval-harness, 코드 채점기):
- 정상 응답(stop_reason=end_turn, 1~120자) → 200 CommentOut
- 빈 값·공백만·120자 초과·비정상 종료(max_tokens·refusal·None) → 503 LLM_UNAVAILABLE
- LLM 오류 → 503 LLM_UNAVAILABLE, 시간 초과 → 504 LLM_TIMEOUT
- 정의 밖 항목(회원 식별자 등)·범위 밖 값 → 422, 응답에 입력값을 되돌려 주지 않는다
- 라우터 전체 2.7초 제한
"""

from __future__ import annotations

import time

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


async def test_non_json_body_is_422(client: httpx.AsyncClient) -> None:
    response = await client.post("/comments", content=b"not json", headers={"Content-Type": "application/json"})

    assert response.status_code == 422
