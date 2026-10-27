"""코치 도구 3종: 사용자 범위, 최신·지정 조회, 월 기본값, 기준값·부하, 오류 코드, 응답 크기."""

from __future__ import annotations

import json
from typing import Any

import pytest

from runkk_ai.coach import TOOL_SPECS, CoachTools
from runkk_ai.runbody import InMemoryResultRepository, MonthlyService, SessionResultOut
from tests.coach.conftest import clock, save
from tests.runbody.conftest import kst

USER = "u_demo"


def test_specs_match_tools(tools: CoachTools) -> None:
    assert [spec["name"] for spec in TOOL_SPECS] == tools.names
    for spec in TOOL_SPECS:
        assert spec["input_schema"]["type"] == "object"
        assert spec["description"]


async def test_latest_session_when_no_id(tools: CoachTools, results: list[SessionResultOut]) -> None:
    out = await tools.call(USER, "getSessionResult", {})

    assert out["sessionId"] == results[1].sessionId
    assert out["startedAt"] == "2026-10-19 19:09"
    assert out["feedback"]["headline"] == "출발이 너무 빨랐어요"
    assert out["prev"]["sessionId"] == results[0].sessionId
    assert "'" in out["pace"]
    assert out["laps"]
    assert out["axes"]["intensity"].endswith(")")


async def test_session_by_id(tools: CoachTools, results: list[SessionResultOut]) -> None:
    out = await tools.call(USER, "getSessionResult", {"sessionId": results[0].sessionId})

    assert out["sessionId"] == results[0].sessionId
    assert out["prev"] is None


@pytest.mark.parametrize("session_id", ["nope", "run-20261019"])
async def test_other_users_session_is_not_found(tools: CoachTools, session_id: str) -> None:
    assert await tools.call("someone_else", "getSessionResult", {"sessionId": session_id}) == {
        "error": "SESSION_NOT_FOUND"
    }


async def test_no_runs_yet(tools: CoachTools) -> None:
    assert await tools.call("new_user", "getSessionResult", {}) == {"error": "NO_RUNS"}
    assert await tools.call("new_user", "getBaseline", {}) == {"error": "NO_RUNS"}


async def test_monthly_defaults_to_this_month(tools: CoachTools) -> None:
    out = await tools.call(USER, "getMonthlyResult", None)

    assert out["yearMonth"] == "2026-10"
    assert out["sessionCount"] == 2
    assert out["distanceKm"] == 5.04
    assert out["bests"]["fastest1km"]["value"] == "5'20\""
    assert out["load"] == {"last7DaysKm": 5.04, "weeklyAvg28DaysKm": 1.26, "acwr": None, "acwrFrom": "2026-11-15"}
    assert out["baseline"] == {"available": False, "needsRuns": 1}


async def test_monthly_other_month_and_bad_input(tools: CoachTools) -> None:
    september = await tools.call(USER, "getMonthlyResult", {"yearMonth": "2026-09"})

    assert september["sessionCount"] == 0
    assert await tools.call(USER, "getMonthlyResult", {"yearMonth": "10월"}) == {"error": "INVALID_YEAR_MONTH"}


async def test_baseline_with_recent_runs(tools: CoachTools, results: list[SessionResultOut]) -> None:
    out = await tools.call(USER, "getBaseline", {})

    assert out["asOf"] == "2026-10-20"
    assert out["baseline"] == {"available": False, "needsRuns": 1}
    assert [r["sessionId"] for r in out["recentRuns"]] == [results[1].sessionId, results[0].sessionId]
    assert out["load"]["acwr"] is None
    assert out["load"]["last7DaysKm"] == 5.04  # 10/18·10/19 모두 10/20 기준 최근 7일
    assert out["recentRuns"][0]["cadence"] == results[1].summary.cadenceMedian


async def test_baseline_available_after_three_runs(results: list[SessionResultOut]) -> None:
    repo = InMemoryResultRepository()
    for r in results:
        await save(repo, r)
    third = results[1].model_copy(update={"sessionId": "run-3"})
    third = third.model_copy(update={"summary": third.summary.model_copy(update={"startedAt": clock()})})
    await save(repo, third)
    tools = CoachTools(repo, MonthlyService(repo, clock=clock), clock=clock)

    out = await tools.call(USER, "getBaseline", {})

    assert out["baseline"]["available"] is True
    assert out["baseline"]["pace"].count("'") == 1


async def test_unknown_tool(tools: CoachTools) -> None:
    assert await tools.call(USER, "deleteEverything", {}) == {"error": "UNKNOWN_TOOL"}


async def test_answers_are_small(tools: CoachTools) -> None:
    """모델 입력 토큰을 아끼려고 줄인다(한국어 기준 대략 1자 = 1토큰 이하)."""
    sizes: dict[str, int] = {}
    for name in tools.names:
        out: dict[str, Any] = await tools.call(USER, name, {})
        sizes[name] = len(json.dumps(out, ensure_ascii=False))
    assert max(sizes.values()) < 2000, sizes


async def test_this_week_is_last_7_days(results: list[SessionResultOut]) -> None:
    """10/27에 물으면 10/18·10/19는 최근 7일 밖이라 '이번 주'는 0 km다(시험에서 모델이 10/19 주를 이번 주로 답했다)."""
    repo = InMemoryResultRepository()
    for r in results:
        await save(repo, r)
    late = kst("2026-10-27 14:00")
    tools = CoachTools(repo, MonthlyService(repo, clock=lambda: late), clock=lambda: late)

    out = await tools.call(USER, "getBaseline", {})

    assert out["load"]["last7DaysKm"] == 0
    assert out["load"]["weeklyAvg28DaysKm"] == 1.26


async def test_acwr_level_after_28_days(results: list[SessionResultOut]) -> None:
    repo = InMemoryResultRepository()
    for r in results:
        await save(repo, r)
    later = kst("2026-11-20 08:00")
    recent = results[1].model_copy(update={"sessionId": "run-1119"})
    recent = recent.model_copy(
        update={"summary": recent.summary.model_copy(update={"startedAt": kst("2026-11-19 19:00")})}
    )
    await save(repo, recent)
    tools = CoachTools(repo, MonthlyService(repo, clock=lambda: later), clock=lambda: later)

    out = await tools.call(USER, "getBaseline", {})

    assert out["load"]["acwr"] == 4.0
    assert out["load"]["level"] == "high"
    assert "acwrFrom" not in out["load"]
