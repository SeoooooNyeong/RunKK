"""최근 부하(ACWR)와 개인 기준값. 이전 결과들의 요약값만 쓰는 순수 함수다(F2, 1회 분석의 acwr_high에도 쓴다).

ACWR = 최근 7일 거리 ÷ (최근 28일 거리 ÷ 4). 첫 러닝부터 28일이 지나야 켠다(그 전에는 28일 평균이 의미 없다).
기준: 0.8 미만 low, 1.5 초과 high(부상 위험이 커지는 구간), 그 사이 normal.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date, datetime, timedelta, timezone
from decimal import ROUND_HALF_UP, Decimal

from runkk_ai.runbody.schemas import AcwrLevel, BaselineOut, LoadOut, Metrics, SessionResultOut

KST = timezone(timedelta(hours=9))  # 한국은 일광 절약 시간이 없어 고정 오프셋으로 충분하다
ACUTE_DAYS = 7
CHRONIC_DAYS = 28
ACWR_LOW = 0.8
ACWR_HIGH = 1.5
BASELINE_MIN_RUNS = 3
BASELINE_MAX_RUNS = 10


def round_half_up(value: float) -> int:
    """앱 화면 숫자와 같게 0.5는 올린다(파이썬 round는 짝수로 맞춘다)."""
    return int(Decimal(str(value)).quantize(Decimal(1), rounding=ROUND_HALF_UP))


def kst_date(moment: datetime) -> date:
    return moment.astimezone(KST).date()


def _sum_between(runs: Sequence[tuple[datetime, int]], start: datetime, end: datetime) -> int:
    return sum(distance for started, distance in runs if start < started <= end)


def _days_tracked(first_started: datetime | None, as_of: datetime) -> int:
    return (kst_date(as_of) - kst_date(first_started)).days if first_started else 0


def _runs(results: Sequence[SessionResultOut]) -> list[tuple[datetime, int]]:
    return [(r.summary.startedAt, r.summary.distanceMeters) for r in results]


def _level(ratio: float) -> AcwrLevel:
    if ratio > ACWR_HIGH:
        return "high"
    if ratio < ACWR_LOW:
        return "low"
    return "normal"


def recent_load(results: Sequence[SessionResultOut], first_started: datetime | None, as_of: datetime) -> LoadOut:
    """as_of 기준 최근 7일·28일 거리. first_started는 이 사용자의 첫 러닝 시각(없으면 None)."""
    runs = _runs(results)
    acute = _sum_between(runs, as_of - timedelta(days=ACUTE_DAYS), as_of)
    chronic = _sum_between(runs, as_of - timedelta(days=CHRONIC_DAYS), as_of)
    weekly = chronic / (CHRONIC_DAYS / ACUTE_DAYS)
    available = first_started is not None and _days_tracked(first_started, as_of) >= CHRONIC_DAYS and chronic > 0
    ratio = round(acute / weekly, 2) if available else None
    return LoadOut(
        available=available,
        ratio=ratio,
        level=_level(ratio) if ratio is not None else None,
        acuteMeters=acute,
        chronicWeeklyMeters=round_half_up(weekly),
        availableFrom=(
            kst_date(first_started) + timedelta(days=CHRONIC_DAYS)
            if first_started is not None and not available
            else None
        ),
    )


def engine_load(
    previous: Sequence[SessionResultOut], first_started: datetime | None, current_distance_m: int, at: datetime
) -> Metrics:
    """1회 분석(coaching.acwr)에 넘기는 부하. 지금 분석하는 러닝(at 시각)의 거리도 넣는다."""
    runs = [*_runs(previous), (at, current_distance_m)]
    first = min(first_started, at) if first_started else at
    return {
        "km_7d": _sum_between(runs, at - timedelta(days=ACUTE_DAYS), at) / 1000,
        "km_28d": _sum_between(runs, at - timedelta(days=CHRONIC_DAYS), at) / 1000,
        "days_tracked": _days_tracked(first, at),
    }


def _mean(values: Sequence[int | None]) -> int | None:
    present = [v for v in values if v is not None]
    return round_half_up(sum(present) / len(present)) if present else None


def baseline(results: Sequence[SessionResultOut]) -> BaselineOut:
    """results는 오래된 순. 최근 BASELINE_MAX_RUNS회 평균이고, BASELINE_MIN_RUNS회 미만이면 값을 내지 않는다."""
    recent = list(results)[-BASELINE_MAX_RUNS:]
    available = len(recent) >= BASELINE_MIN_RUNS
    summaries = [r.summary for r in recent] if available else []
    return BaselineOut(
        available=available,
        sessionCount=len(recent),
        needs=max(BASELINE_MIN_RUNS - len(recent), 0),
        paceSecondsPerKm=_mean([s.paceSecondsPerKm for s in summaries]),
        avgHeartRate=_mean([s.avgHeartRate for s in summaries]),
        cadenceMedian=_mean([s.cadenceMedian for s in summaries]),
        recoveryHeartRate1m=_mean([s.recoveryHeartRate1m for s in summaries]),
    )
