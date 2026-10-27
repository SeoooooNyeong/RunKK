"""지표 → 5각 점수(0~100)와 판정 라벨.

v0: 참조값 표 + 선형 보간. 사용자가 쌓이면 분위수로 바꾼다.
label·level은 앱이 그대로 보여 주는 문구다. 앱이 임계값을 다시 판단하지 않도록 기준은 여기 한 곳에만 둔다.
"""

from __future__ import annotations

from runkk_ai.runbody.metrics import OVERSTRIDE_CADENCE, OVERSTRIDE_STRIDE_M
from runkk_ai.runbody.schemas import AxisLevel, AxisName, AxisOut, Metrics

# 축별 기준선(같은 연령대 일반 러너의 대략적인 위치). 5각 차트의 점선
REF: dict[AxisName, int] = {"intensity": 55, "endurance": 60, "stability": 60, "form": 60, "fitness": 55}


def _lin(x: float | None, lo: float, hi: float, lo_score: float, hi_score: float) -> int | None:
    """x가 lo면 lo_score, hi면 hi_score. 범위 밖은 끝값으로 자른다."""
    if x is None:
        return None
    t = min(1.0, max(0.0, (x - lo) / (hi - lo)))
    return round(lo_score + (hi_score - lo_score) * t)


def _num(section: Metrics, key: str) -> float | None:
    value = section.get(key)
    return None if value is None else float(value)


def _intensity(m: Metrics) -> AxisOut:
    # Z4 이상 비율. 높을수록 '세게 뛴' 성향이고 좋고 나쁨은 아니다. 0% → 20점, 60% → 90점
    z4 = _num(m, "z4_plus_pct")
    score = _lin(z4, 0, 60, 20, 90)
    if z4 is None:
        return _axis("intensity", None, None, "심박 없음", "none")
    if z4 >= 40:
        return _axis("intensity", score, z4, "높음", "warn")
    if z4 >= 30:
        return _axis("intensity", score, z4, "다소 높음", "mid")
    return _axis("intensity", score, z4, "적정", "good")


def _endurance(m: Metrics) -> AxisOut:
    # 전반 대비 후반 효율(속도/심박) 변화. -20% → 20점, 0% → 70점, +5% → 90점
    e = _num(m, "efficiency_change")
    if e is None:
        return _axis("endurance", None, None, "데이터 부족", "none")
    score = _lin(e, -0.20, 0.0, 20, 70) if e < 0 else _lin(e, 0.0, 0.05, 70, 90)
    pct = round(e * 100, 1)
    if pct <= -10:
        return _axis("endurance", score, pct, "후반에 처짐", "warn")
    if pct <= -5:
        return _axis("endurance", score, pct, "약간 처짐", "mid")
    return _axis("endurance", score, pct, "유지", "good")


def _stability(m: Metrics) -> AxisOut:
    # 30초 구간 페이스 변동계수. 15% → 30점, 3% → 95점
    cv = _num(m, "pace_cv")
    if cv is None:
        return _axis("stability", None, None, "데이터 부족", "none")
    score = _lin(-cv, -0.15, -0.03, 30, 95)
    pct = round(cv * 100, 1)
    if pct >= 10:
        return _axis("stability", score, pct, "들쭉날쭉", "warn")
    if pct >= 8:
        return _axis("stability", score, pct, "보통", "mid")
    return _axis("stability", score, pct, "일정", "good")


def _form(m: Metrics) -> AxisOut:
    # 케이던스. 150 → 30점, 180 → 95점
    cadence, stride = _num(m, "cadence_median"), _num(m, "stride_m")
    if cadence is None:
        return _axis("form", None, None, "케이던스 없음", "none")
    score = _lin(cadence, 150, 180, 30, 95)
    if cadence < OVERSTRIDE_CADENCE and stride is not None and stride >= OVERSTRIDE_STRIDE_M:
        return _axis("form", score, cadence, "보폭이 긴 편", "warn")
    return _axis("form", score, cadence, "양호", "good")


def _fitness(m: Metrics) -> AxisOut:
    # 점수는 VO2max 추정(35 → 30점, 60 → 95점), 라벨은 체감이 쉬운 1분 회복 심박으로 단다
    score = _lin(_num(m, "vo2max_est"), 35, 60, 30, 95)
    r1 = _num(m, "recovery_hr_1m")
    if r1 is None:
        return _axis("fitness", score, None, "회복 심박 없음", "none" if score is None else "mid")
    if r1 <= -20:
        return _axis("fitness", score, r1, "회복 빠름", "good")
    if r1 <= -12:
        return _axis("fitness", score, r1, "보통", "mid")
    return _axis("fitness", score, r1, "회복 느림", "warn")


def _axis(name: AxisName, score: int | None, value: float | None, label: str, level: AxisLevel) -> AxisOut:
    return AxisOut(score=score, ref=REF[name], value=value, label=label, level=level)


def score_axes(metrics: Metrics) -> dict[AxisName, AxisOut]:
    return {
        "intensity": _intensity(metrics.get("intensity", {})),
        "endurance": _endurance(metrics.get("endurance", {})),
        "stability": _stability(metrics.get("stability", {})),
        "form": _form(metrics.get("form", {})),
        "fitness": _fitness(metrics.get("fitness", {})),
    }
