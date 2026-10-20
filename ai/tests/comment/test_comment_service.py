"""CommentService: Semaphore(4), 프로세스 하루 상한, 출력 검증, 구조화 로그(D-58, D-62)."""

from __future__ import annotations

import asyncio
import logging
from datetime import date

import pytest

from runkk_ai.comment import CommentService, PromptBuilder, SummaryIn
from runkk_ai.comment import service as service_module
from tests.conftest import VALID_SUMMARY, FakeLlmClient

SUMMARY = SummaryIn.model_validate(VALID_SUMMARY)


async def test_generate_returns_ok_result(fake_llm: FakeLlmClient) -> None:
    service = CommentService(llm=fake_llm, daily_cap=240)

    result = await service.generate(SUMMARY)

    assert result.outcome == "ok"
    assert result.text == fake_llm.text


async def test_at_most_four_llm_calls_run_at_once() -> None:
    gate = asyncio.Event()
    llm = FakeLlmClient(gate=gate)
    service = CommentService(llm=llm, daily_cap=240)

    tasks = [asyncio.create_task(service.generate(SUMMARY)) for _ in range(10)]
    await asyncio.sleep(0.05)
    assert llm.active == 4
    gate.set()
    results = await asyncio.gather(*tasks)

    assert llm.max_active == 4
    assert [r.outcome for r in results] == ["ok"] * 10


async def test_daily_cap_stops_calling_llm(fake_llm: FakeLlmClient) -> None:
    service = CommentService(llm=fake_llm, daily_cap=2)

    results = [await service.generate(SUMMARY) for _ in range(3)]

    assert [r.outcome for r in results] == ["ok", "ok", "error"]
    assert len(fake_llm.prompts) == 2


async def test_failed_calls_count_toward_daily_cap() -> None:
    llm = FakeLlmClient(error=RuntimeError("boom"))
    service = CommentService(llm=llm, daily_cap=1)

    first = await service.generate(SUMMARY)
    llm.error = None
    second = await service.generate(SUMMARY)

    assert (first.outcome, second.outcome) == ("error", "error")
    assert len(llm.prompts) == 1


async def test_concurrent_requests_never_exceed_daily_cap() -> None:
    llm = FakeLlmClient(delay_s=0.01)
    service = CommentService(llm=llm, daily_cap=5)

    results = await asyncio.gather(*(service.generate(SUMMARY) for _ in range(20)))

    assert sum(r.outcome == "ok" for r in results) == 5
    assert len(llm.prompts) == 5


async def test_daily_cap_resets_on_next_korean_day(fake_llm: FakeLlmClient, monkeypatch: pytest.MonkeyPatch) -> None:
    today = {"value": date(2026, 10, 20)}
    monkeypatch.setattr(service_module, "_today", lambda: today["value"])
    service = CommentService(llm=fake_llm, daily_cap=1)

    assert (await service.generate(SUMMARY)).outcome == "ok"
    assert (await service.generate(SUMMARY)).outcome == "error"
    today["value"] = date(2026, 10, 21)

    assert (await service.generate(SUMMARY)).outcome == "ok"


def test_today_uses_korean_time() -> None:
    assert isinstance(service_module._today(), date)


async def test_timeout_from_llm_is_timeout(fake_llm: FakeLlmClient) -> None:
    fake_llm.error = TimeoutError()
    service = CommentService(llm=fake_llm, daily_cap=240)

    result = await service.generate(SUMMARY)

    assert result.outcome == "timeout"
    assert result.text is None


async def test_cancellation_propagates_and_is_logged(caplog: pytest.LogCaptureFixture) -> None:
    llm = FakeLlmClient(delay_s=5.0)
    service = CommentService(llm=llm, daily_cap=240)
    caplog.set_level(logging.INFO, logger="runkk_ai.comment")

    with pytest.raises(TimeoutError):
        async with asyncio.timeout(0.05):
            await service.generate(SUMMARY)

    assert "result=timeout" in caplog.text


async def test_log_has_metrics_but_no_prompt_or_reply(
    fake_llm: FakeLlmClient, caplog: pytest.LogCaptureFixture
) -> None:
    caplog.set_level(logging.INFO, logger="runkk_ai.comment")
    service = CommentService(llm=fake_llm, daily_cap=240)

    await service.generate(SUMMARY)

    records = [r for r in caplog.records if r.name.startswith("runkk_ai.comment")]
    assert len(records) == 1
    message = records[0].getMessage()
    for key in ("model=fake-model", "input_tokens=111", "output_tokens=22", "latency_ms=", "result=ok"):
        assert key in message
    assert fake_llm.text not in caplog.text
    assert fake_llm.prompts[0] not in caplog.text


async def test_error_log_names_only_exception_type(caplog: pytest.LogCaptureFixture) -> None:
    llm = FakeLlmClient(error=RuntimeError("secret body from provider"))
    caplog.set_level(logging.INFO, logger="runkk_ai.comment")

    await CommentService(llm=llm, daily_cap=240).generate(SUMMARY)

    assert "result=error" in caplog.text
    assert "RuntimeError" in caplog.text
    assert "secret body" not in caplog.text


def test_prompt_uses_only_aggregates() -> None:
    prompt = PromptBuilder().build(SUMMARY)

    assert "42.5" in prompt
    assert "1200" in prompt or "1.2" in prompt
    assert "토요일" in prompt
    assert "6~9시" in prompt
    assert "10회" in prompt
    assert "120자" in prompt


@pytest.mark.parametrize(("band", "label"), [(0, "0~3시"), (7, "21~24시")])
def test_prompt_hour_band_label(band: int, label: str) -> None:
    summary = SummaryIn.model_validate({**VALID_SUMMARY, "frequentHourBand": band})

    assert label in PromptBuilder().build(summary)


@pytest.mark.parametrize(("pace", "word"), [(-8, "빨라"), (5, "느려"), (0, "같")])
def test_prompt_describes_pace_direction(pace: int, word: str) -> None:
    summary = SummaryIn.model_validate({**VALID_SUMMARY, "paceChangeSeconds": pace})

    assert word in PromptBuilder().build(summary)
