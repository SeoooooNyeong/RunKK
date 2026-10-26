"""월간 RUN바디(F2). 1회 분석 결과를 한 달 단위로 모은다. 순수 함수다(저장소·시계를 모른다).

- 월 요약·5각 평균·최고 기록·주간 거리·밤/낮 비율은 그 달(한국 시간) 러닝만 센다.
- 부하(ACWR)·개인 기준값·아바타 입력값은 as_of 기준이라 지난달 러닝도 본다.
"""

from __future__ import annotations

from collections.abc import Callable, Sequence
from datetime import date, datetime, timedelta
from itertools import pairwise

from runkk_ai.runbody.load import KST, baseline, kst_date, recent_load, round_half_up
from runkk_ai.runbody.schemas import (
    AXIS_NAMES,
    AvatarInputsOut,
    AxisName,
    BestOut,
    BestsOut,
    MonthlyAxisOut,
    MonthlyOut,
    SessionResultOut,
    TimeOfDayOut,
    WeekOut,
)
from runkk_ai.runbody.scoring import REF

NIGHT_FROM_HOUR = 18  # 18:00~05:59 시작은 밤 러닝
NIGHT_UNTIL_HOUR = 6
AVATAR_WINDOW_DAYS = 14


def month_range(year_month: str) -> tuple[datetime, datetime]:
    """'2026-10' → [10/1 00:00, 11/1 00:00) 한국 시간. 형식이 틀리면 ValueError."""
    start = datetime.strptime(year_month, "%Y-%m").replace(tzinfo=KST)
    end = (start + timedelta(days=32)).replace(day=1)
    return start, end


def aggregate_month(
    user_id: str,
    year_month: str,
    results: Sequence[SessionResultOut],
    as_of: datetime,
    first_started: datetime | None,
) -> MonthlyOut:
    """results는 이 사용자의 완료된 결과(오래된 순, as_of 이전 28일 이상 포함 권장)."""
    start, end = month_range(year_month)
    ordered = sorted(results, key=lambda r: r.summary.startedAt)
    in_month = [r for r in ordered if start <= r.summary.startedAt < end and r.summary.startedAt <= as_of]
    until_now = [r for r in ordered if r.summary.startedAt <= as_of]
    distance = sum(r.summary.distanceMeters for r in in_month)
    active_ms = sum(r.summary.activeMs for r in in_month)
    recent14 = [r for r in until_now if r.summary.startedAt > as_of - timedelta(days=AVATAR_WINDOW_DAYS)]
    return MonthlyOut(
        userId=user_id,
        yearMonth=year_month,
        asOf=as_of,
        sessionCount=len(in_month),
        distanceMeters=distance,
        activeMs=active_ms,
        paceSecondsPerKm=round_half_up(active_ms / 1000 / (distance / 1000)) if distance else None,
        avgHeartRate=_time_weighted_hr(in_month),
        axes={name: _axis(name, in_month) for name in AXIS_NAMES},
        bests=_bests(in_month),
        weeks=_weeks(in_month),
        timeOfDay=_time_of_day(in_month),
        load=recent_load(until_now, first_started, as_of),
        baseline=baseline(until_now),
        avatarInputs=AvatarInputsOut(
            distance14dMeters=sum(r.summary.distanceMeters for r in recent14), sessions14d=len(recent14)
        ),
    )


def _axis(name: AxisName, results: Sequence[SessionResultOut]) -> MonthlyAxisOut:
    scores = [s for r in results if (s := r.axes[name].score) is not None]
    average = round_half_up(sum(scores) / len(scores)) if scores else None
    return MonthlyAxisOut(average=average, count=len(scores), ref=REF[name])


def _time_weighted_hr(results: Sequence[SessionResultOut]) -> int | None:
    pairs = [(r.summary.avgHeartRate, r.summary.activeMs) for r in results if r.summary.avgHeartRate is not None]
    total = sum(ms for _, ms in pairs)
    return round_half_up(sum(hr * ms for hr, ms in pairs) / total) if total else None


def _best(
    results: Sequence[SessionResultOut], value: Callable[[SessionResultOut], int | None], *, lowest: bool
) -> BestOut | None:
    candidates = [(v, r) for r in results if (v := value(r)) is not None]
    if not candidates:
        return None
    # min·max는 같은 값이면 앞의 것을 고른다 → 먼저 세운 기록을 남긴다
    best_value, best = (min if lowest else max)(candidates, key=lambda c: c[0])
    return BestOut(value=best_value, sessionId=best.sessionId, runDate=kst_date(best.summary.startedAt))


def _fastest_km(result: SessionResultOut) -> int | None:
    full = [lap.paceSeconds for lap in result.laps if not lap.partial]
    return min(full) if full else None


def _fastest_2km(result: SessionResultOut) -> int | None:
    full = [lap.paceSeconds for lap in result.laps if not lap.partial]
    pairs = [a + b for a, b in pairwise(full)]
    return min(pairs) if pairs else None


def _bests(results: Sequence[SessionResultOut]) -> BestsOut:
    return BestsOut(
        fastestKmSeconds=_best(results, _fastest_km, lowest=True),
        fastest2kmSeconds=_best(results, _fastest_2km, lowest=True),
        longestRunMeters=_best(results, lambda r: r.summary.distanceMeters, lowest=False),
        bestRecoveryHeartRate1m=_best(results, lambda r: r.summary.recoveryHeartRate1m, lowest=True),
    )


def _weeks(results: Sequence[SessionResultOut]) -> list[WeekOut]:
    weeks: dict[date, tuple[int, int]] = {}
    for r in results:
        day = kst_date(r.summary.startedAt)
        monday = day - timedelta(days=day.weekday())
        distance, count = weeks.get(monday, (0, 0))
        weeks[monday] = (distance + r.summary.distanceMeters, count + 1)
    return [WeekOut(weekStart=monday, distanceMeters=d, sessionCount=c) for monday, (d, c) in sorted(weeks.items())]


def _time_of_day(results: Sequence[SessionResultOut]) -> TimeOfDayOut:
    hours = [r.summary.startedAt.astimezone(KST).hour for r in results]
    night = sum(1 for h in hours if h >= NIGHT_FROM_HOUR or h < NIGHT_UNTIL_HOUR)
    return TimeOfDayOut(night=night, day=len(hours) - night)
