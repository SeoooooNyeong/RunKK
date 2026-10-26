"""월간 RUN바디(F2) 집계 시험. 실측 러닝 2회(10/18 00:20, 10/19 19:09)가 10월에 들어 있다."""

from __future__ import annotations

from datetime import date

from runkk_ai.runbody.monthly import aggregate_month, month_range
from runkk_ai.runbody.schemas import SessionResultOut
from tests.runbody.conftest import kst, retime

AS_OF = kst("2026-10-26 14:00")


def test_october_summary(first_result: SessionResultOut, second_result: SessionResultOut) -> None:
    month = aggregate_month("u_demo", "2026-10", [first_result, second_result], AS_OF, first_started=None)

    assert (month.sessionCount, month.distanceMeters, month.activeMs) == (2, 5035, 1853010)
    assert month.paceSecondsPerKm == 368  # 1853.01 s / 5.035 km
    assert month.avgHeartRate == 154  # 시간 가중 평균
    assert {k: a.average for k, a in month.axes.items()} == {
        "intensity": 75,
        "endurance": 45,
        "stability": 63,
        "form": 64,
        "fitness": 73,
    }
    assert month.axes["form"].count == 2


def test_october_bests(first_result: SessionResultOut, second_result: SessionResultOut) -> None:
    bests = aggregate_month("u_demo", "2026-10", [first_result, second_result], AS_OF, first_started=None).bests

    assert bests.fastestKmSeconds is not None
    assert (bests.fastestKmSeconds.sessionId, bests.fastestKmSeconds.runDate) == ("run-20261019", date(2026, 10, 19))
    assert bests.fastestKmSeconds.value == min(lap.paceSeconds for lap in second_result.laps if not lap.partial)
    assert bests.fastest2kmSeconds is not None
    assert bests.longestRunMeters is not None
    assert (bests.longestRunMeters.value, bests.longestRunMeters.runDate) == (3008, date(2026, 10, 19))
    assert bests.bestRecoveryHeartRate1m is not None
    assert bests.bestRecoveryHeartRate1m.value == -27


def test_october_weeks_and_time_of_day(first_result: SessionResultOut, second_result: SessionResultOut) -> None:
    month = aggregate_month("u_demo", "2026-10", [first_result, second_result], AS_OF, first_started=None)

    # 10/18(일)은 10/12 주, 10/19(월)는 10/19 주
    assert [(w.weekStart, w.distanceMeters, w.sessionCount) for w in month.weeks] == [
        (date(2026, 10, 12), 2027, 1),
        (date(2026, 10, 19), 3008, 1),
    ]
    assert (month.timeOfDay.night, month.timeOfDay.day) == (2, 0)  # 00:20, 19:09


def test_october_load_baseline_avatar(first_result: SessionResultOut, second_result: SessionResultOut) -> None:
    month = aggregate_month(
        "u_demo", "2026-10", [first_result, second_result], AS_OF, first_started=first_result.summary.startedAt
    )

    assert (month.load.available, month.load.availableFrom) == (False, date(2026, 11, 15))
    assert (month.baseline.available, month.baseline.needs) == (False, 1)
    assert (month.avatarInputs.distance14dMeters, month.avatarInputs.sessions14d) == (5035, 2)


def test_only_runs_in_month_are_summarized(first_result: SessionResultOut, second_result: SessionResultOut) -> None:
    """부하·기준값은 지난달 러닝까지 보지만, 월 요약은 이번 달 러닝만 센다."""
    september = retime(first_result, "sep", kst("2026-09-30 23:50"), 4000)

    month = aggregate_month("u_demo", "2026-10", [september, first_result, second_result], AS_OF, first_started=None)

    assert month.sessionCount == 2
    assert month.load.chronicWeeklyMeters == 2259  # (4000 + 2027 + 3008) / 4


def test_empty_month(first_result: SessionResultOut) -> None:
    month = aggregate_month("u_demo", "2026-11", [first_result], kst("2026-11-03 09:00"), first_started=None)

    assert (month.sessionCount, month.distanceMeters, month.paceSecondsPerKm, month.avgHeartRate) == (0, 0, None, None)
    assert all(a.average is None and a.count == 0 for a in month.axes.values())
    assert month.bests.fastestKmSeconds is None
    assert month.weeks == []


def test_month_range_is_kst() -> None:
    start, end = month_range("2026-12")

    assert (start, end) == (kst("2026-12-01 00:00"), kst("2027-01-01 00:00"))
