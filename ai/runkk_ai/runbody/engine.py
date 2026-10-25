"""세션 → RUN바디 1회 분석 결과. 전처리·지표·점수·코칭·피드백을 조립하는 곳이다.

순수 함수다(DB·HTTP·시계를 모른다). 같은 입력과 history면 항상 같은 결과가 나온다.
CPU를 쓰는 pandas 계산이라 서비스가 별도 스레드에서 부른다.
"""

from __future__ import annotations

import math
import re
from collections.abc import Sequence
from typing import Any

from runkk_ai.runbody import coaching, feedback, metrics, preprocess, scoring
from runkk_ai.runbody.feedback import FeedbackInput
from runkk_ai.runbody.schemas import (
    CoachingOut,
    LapOut,
    Metrics,
    PrevOut,
    QualityFlag,
    SessionIn,
    SessionResultOut,
    SummaryOut,
)

GPS_LOCK_SLOW_S = 30
PRE_GPS_ESTIMATED_M = 100
SHORT_RUN_M = 2000
_SNAKE = re.compile(r"_([a-z0-9])")


def analyze_session(session: SessionIn, history: Sequence[SessionResultOut]) -> SessionResultOut:
    """history는 같은 사용자의 이전 결과(오래된 순). 회복 추세와 지난 러닝 비교에 쓴다."""
    grid = preprocess.to_grid(session)
    hr_max = preprocess.hr_max_for(session.profile.birthYear, session.startedAt.date(), session.profile.hrMax)

    laps = metrics.laps(grid)
    m: Metrics = {
        "intensity": metrics.intensity(grid, hr_max),
        "endurance": metrics.endurance(grid),
        "stability": metrics.stability(grid, laps),
        "form": metrics.form(grid),
        "fitness": metrics.fitness(grid, hr_max, session.recoveryHeartRates),
        "course": metrics.course(grid, session.distanceMeters),
    }
    distance_m = round(session.distanceMeters)
    pace = round(session.activeMs / 1000 / (session.distanceMeters / 1000))
    flags, detail = coaching.evaluate(grid, m, pace, hr_max, history)
    prev = history[-1] if history else None

    summary = SummaryOut(
        distanceMeters=distance_m,
        activeMs=session.activeMs,
        paceSecondsPerKm=pace,
        avgHeartRate=m["intensity"].get("avg_hr"),
        maxHeartRate=m["intensity"].get("max_hr"),
        cadenceMedian=m["form"].get("cadence_median"),
        recoveryHeartRate1m=m["fitness"].get("recovery_hr_1m"),
        startedAt=session.startedAt,
        source=session.source,
    )
    text = feedback.compose(
        FeedbackInput(
            distance_m=distance_m,
            pace_s_per_km=pace,
            metrics=m,
            flags=flags,
            detail=detail,
            prev_distance_m=prev.summary.distanceMeters if prev else None,
            prev_recovery_1m=prev.summary.recoveryHeartRate1m if prev else None,
        )
    )
    return SessionResultOut(
        sessionId=session.sessionId,
        userId=session.userId,
        summary=summary,
        axes=scoring.score_axes(m),
        metrics=_camel(m),
        coaching=CoachingOut(flags=flags, detail=_camel(detail)),
        laps=[_lap(lap) for lap in laps],
        prev=_prev(prev),
        feedback=text,
        qualityFlags=_quality_flags(m, distance_m),
    )


def _lap(lap: Metrics) -> LapOut:
    return LapOut(
        km=lap["km"],
        paceSeconds=lap["pace_s"],
        avgHeartRate=lap.get("avg_hr"),
        partial=bool(lap.get("partial", False)),
    )


def _prev(prev: SessionResultOut | None) -> PrevOut | None:
    if prev is None:
        return None
    return PrevOut(
        sessionId=prev.sessionId,
        distanceMeters=prev.summary.distanceMeters,
        paceSecondsPerKm=prev.summary.paceSecondsPerKm,
        recoveryHeartRate1m=prev.summary.recoveryHeartRate1m,
    )


def _quality_flags(m: Metrics, distance_m: int) -> list[QualityFlag]:
    course = m["course"]
    flags: list[QualityFlag] = []
    if course.get("gps") and course.get("gps_lock_delay_s", 0) > GPS_LOCK_SLOW_S:
        flags.append("gps_lock_slow")
    if course.get("gps") and course.get("pre_gps_distance_m", 0) > PRE_GPS_ESTIMATED_M:
        flags.append("pre_gps_distance_estimated")
    if distance_m < SHORT_RUN_M:
        flags.append("short_run")
    if not m["intensity"]:
        flags.append("no_heart_rate")
    if not course.get("gps"):
        flags.append("no_gps")
    if m["form"].get("insufficient"):
        flags.append("no_cadence")
    return flags


def _camel(value: Any) -> Any:
    """응답용으로 키를 camelCase로 바꾸고, JSON에 실을 수 없는 NaN·무한대는 None으로 바꾼다."""
    if isinstance(value, dict):
        return {_SNAKE.sub(lambda g: g.group(1).upper(), str(k)): _camel(v) for k, v in value.items()}
    if isinstance(value, list | tuple):
        return [_camel(v) for v in value]
    if isinstance(value, float) and not math.isfinite(value):
        return None
    return value
