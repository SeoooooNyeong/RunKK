"""최근 부하(ACWR)·개인 기준값 시험. 기준: ACWR = 최근 7일 거리 ÷ (최근 28일 거리 ÷ 4), 첫 러닝부터 28일 뒤에 켠다."""

from __future__ import annotations

from datetime import date

import pytest

from runkk_ai.runbody.load import baseline, engine_load, recent_load
from runkk_ai.runbody.schemas import SessionResultOut
from tests.runbody.conftest import kst, retime

AS_OF = kst("2026-10-26 14:00")


def _runs(base: SessionResultOut, *days: str, distance_m: int = 5000) -> list[SessionResultOut]:
    return [retime(base, f"r{i}", kst(f"{d} 07:00"), distance_m) for i, d in enumerate(days)]


def test_load_waits_28_days_from_first_run(first_result: SessionResultOut, second_result: SessionResultOut) -> None:
    load = recent_load([first_result, second_result], first_started=first_result.summary.startedAt, as_of=AS_OF)

    assert load.available is False
    assert load.ratio is None
    assert load.level is None
    assert load.availableFrom == date(2026, 11, 15)
    assert load.acuteMeters == 3008  # 10/19만 최근 7일 안
    assert load.chronicWeeklyMeters == 1259  # (2027 + 3008) / 4


@pytest.mark.parametrize(
    ("days", "ratio", "level"),
    [
        (("2026-10-01", "2026-10-08", "2026-10-15", "2026-10-20", "2026-10-22", "2026-10-24"), 2.0, "high"),
        (("2026-09-30", "2026-10-07", "2026-10-14", "2026-10-21"), 1.0, "normal"),
        (("2026-09-30", "2026-10-07", "2026-10-14"), 0.0, "low"),
    ],
)
def test_load_level(first_result: SessionResultOut, days: tuple[str, ...], ratio: float, level: str) -> None:
    runs = _runs(first_result, *days)

    load = recent_load(runs, first_started=kst("2026-09-01 07:00"), as_of=AS_OF)

    assert load.available is True
    assert load.ratio == ratio
    assert load.level == level
    assert load.availableFrom is None


def test_load_without_runs_is_empty() -> None:
    load = recent_load([], first_started=None, as_of=AS_OF)

    assert (load.available, load.acuteMeters, load.chronicWeeklyMeters, load.availableFrom) == (False, 0, 0, None)


def test_engine_load_counts_current_run(first_result: SessionResultOut) -> None:
    """1회 분석에 넘기는 부하에는 지금 분석하는 러닝 거리도 들어간다."""
    previous = _runs(first_result, "2026-10-01", "2026-10-08", "2026-10-15", "2026-10-22", "2026-10-24")

    load = engine_load(previous, kst("2026-09-01 07:00"), current_distance_m=5000, at=AS_OF)

    assert load == {"km_7d": 15.0, "km_28d": 30.0, "days_tracked": 55}


def test_baseline_needs_three_runs(first_result: SessionResultOut, second_result: SessionResultOut) -> None:
    base = baseline([first_result, second_result])

    assert (base.available, base.sessionCount, base.needs) == (False, 2, 1)
    assert base.paceSecondsPerKm is None


def test_baseline_averages_recent_runs(first_result: SessionResultOut, second_result: SessionResultOut) -> None:
    base = baseline([first_result, second_result, second_result])

    assert (base.available, base.sessionCount, base.needs) == (True, 3, 0)
    assert base.paceSecondsPerKm == 367  # (379 + 361 + 361) / 3
    assert base.avgHeartRate == 155  # (150 + 157 + 157) / 3 = 154.7
    assert base.recoveryHeartRate1m == -23  # (-14 - 27 - 27) / 3 = -22.7


def test_baseline_uses_last_ten(first_result: SessionResultOut, second_result: SessionResultOut) -> None:
    base = baseline([first_result] * 5 + [second_result] * 10)

    assert base.sessionCount == 10
    assert base.paceSecondsPerKm == 361
