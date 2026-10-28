"""코칭 플래그 5개(러닝 훈련 원칙 기반). 지표 위에 규칙만 얹는다.

warmup_missing   출발 150초 안에 Z4 도달, 또는 첫 500 m 페이스가 평균보다 10% 이상 빠름
positive_split   후반 페이스가 전반보다 5% 이상 느림
overstride       케이던스 < 170 이고 보폭 >= 1.0 m (케이던스가 없으면 판단하지 않음)
recovery_slower  1분 회복량이 직전 3회 평균보다 5 bpm 이상 둔화(이전 기록 3회부터)
acwr_high        7일 부하 ÷ (28일 부하 / 4) > 1.5 (28일 기록이 있을 때만. 이 MR에서는 부하를 받지 않아 항상 생략)
"""

from __future__ import annotations

from collections.abc import Sequence

import pandas as pd

from runkk_ai.runbody.metrics import MIN_MOVING_S, OVERSTRIDE_CADENCE, OVERSTRIDE_STRIDE_M
from runkk_ai.runbody.schemas import CoachingFlag, Metrics, SessionResultOut

WARMUP_WINDOW_S = 150
FIRST_SEGMENT_M = 500
FAST_START_RATIO = 0.9
Z4_RATIO = 0.8
SPLIT_POSITIVE = 0.05
RECOVERY_SLOW_BPM = 5
RECOVERY_HISTORY = 3
ACWR_HIGH = 1.5


def warmup(g: pd.DataFrame, hr_max: int, avg_pace_s: float) -> Metrics:
    head = g[g["t"] < WARMUP_WINDOW_S]
    hit = head[head["hr"] >= hr_max * Z4_RATIO]
    z4_at = int(hit["t"].iloc[0]) if len(hit) else None
    segment = g[g["dist_cum"] <= FIRST_SEGMENT_M]
    first_pace: float | None = None
    if len(segment) > 10 and float(segment["dist_cum"].iloc[-1]) > 0:
        first_pace = float(segment["t"].iloc[-1]) / (float(segment["dist_cum"].iloc[-1]) / 1000)
    fast_start = first_pace is not None and first_pace < avg_pace_s * FAST_START_RATIO
    return {
        "z4_reached_at_s": z4_at,
        "first_500m_pace_s": round(first_pace) if first_pace else None,
        "fast_start": fast_start,
        "missing": z4_at is not None or fast_start,
    }


def split(g: pd.DataFrame) -> Metrics:
    moving = g[g["moving"]]
    if len(moving) < MIN_MOVING_S:
        return {"type": None}
    half = len(moving) // 2
    speed_a, speed_b = float(moving["speed"].iloc[:half].mean()), float(moving["speed"].iloc[half:].mean())
    if speed_a <= 0 or speed_b <= 0:
        return {"type": None}
    a, b = 1000 / speed_a, 1000 / speed_b
    ratio = b / a - 1
    kind = "positive" if ratio > SPLIT_POSITIVE else ("negative" if ratio < -SPLIT_POSITIVE else "even")
    return {"type": kind, "first_half_pace_s": round(a), "second_half_pace_s": round(b), "ratio": round(ratio, 3)}


def overstride(form: Metrics) -> Metrics:
    cadence, stride = form.get("cadence_median"), form.get("stride_m")
    if cadence is None or stride is None:
        return {"flag": False, "skipped": "no_cadence"}
    flag = cadence < OVERSTRIDE_CADENCE and stride >= OVERSTRIDE_STRIDE_M
    return {"flag": flag, "cadence": cadence, "stride_m": stride}


def target_stride(speed_mps: float, target_cadence: int = OVERSTRIDE_CADENCE) -> float:
    return round(speed_mps / (target_cadence / 60), 2)


def recovery_trend(current: int | None, history: Sequence[SessionResultOut]) -> Metrics:
    """history는 같은 사용자의 이전 결과(오래된 순). 1분 회복량은 음수일수록 좋다."""
    previous = [
        r.summary.recoveryHeartRate1m for r in history[-RECOVERY_HISTORY:] if r.summary.recoveryHeartRate1m is not None
    ]
    if current is None or len(previous) < RECOVERY_HISTORY:
        return {"flag": False, "needs": RECOVERY_HISTORY - len(previous) if current is not None else None}
    base = sum(previous) / len(previous)
    slower = current - base  # 회복량이 -27 → -20이면 +7 둔화
    return {
        "flag": slower >= RECOVERY_SLOW_BPM,
        "baseline_1m": round(base, 1),
        "current_1m": current,
        "delta": round(slower, 1),
    }


def acwr(load: Metrics | None) -> Metrics:
    if not load or not load.get("km_28d") or load.get("days_tracked", 0) < 28:
        return {"flag": False, "ratio": None, "reason": "28일 데이터 부족"}
    ratio = float(load["km_7d"]) / max(float(load["km_28d"]) / 4, 0.1)
    return {"flag": ratio > ACWR_HIGH, "ratio": round(ratio, 2)}


def evaluate(
    g: pd.DataFrame,
    metrics: Metrics,
    pace_s_per_km: float,
    hr_max: int,
    history: Sequence[SessionResultOut],
    load: Metrics | None = None,
) -> tuple[list[CoachingFlag], Metrics]:
    """(플래그 목록, 판단 근거). 근거 키는 snake_case이고 응답으로 나갈 때 바뀐다."""
    detail: Metrics = {
        "warmup": warmup(g, hr_max, pace_s_per_km),
        "split": split(g),
        "overstride": overstride(metrics.get("form", {})),
        "recovery": recovery_trend(metrics.get("fitness", {}).get("recovery_hr_1m"), history),
        "acwr": acwr(load),
    }
    flags: list[CoachingFlag] = []
    if detail["warmup"]["missing"]:
        flags.append("warmup_missing")
    if detail["split"]["type"] == "positive":
        flags.append("positive_split")
    if detail["overstride"]["flag"]:
        flags.append("overstride")
    if detail["recovery"]["flag"]:
        flags.append("recovery_slower")
    if detail["acwr"]["flag"]:
        flags.append("acwr_high")
    return flags, detail
