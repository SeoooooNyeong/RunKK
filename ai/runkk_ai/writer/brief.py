"""분석 결과 → writer 입력 요약(brief). 학습 데이터와 실제 추론이 같은 함수를 쓴다.

writer는 숫자를 계산하지 않고 이 요약에 있는 값만 옮겨 적는다. 그래서 페이스·거리처럼 사람이 읽는
형태는 여기서 미리 만들어 둔다(모델이 6'05"를 직접 계산하다 틀리지 않게).
판단(어떤 고칠 점·좋은 점을 말할지)은 엔진의 템플릿 규칙이 이미 정했다. writer는 그 판단을 자연스러운 말로 바꾼다.
"""

from __future__ import annotations

from typing import Any

from runkk_ai.runbody.schemas import SessionResultOut

BRIEF_VERSION = 1
_AXES = ("intensity", "endurance", "stability", "form", "fitness")


def pace_text(seconds: float | None) -> str | None:
    """초 → 6'05\" 형식."""
    if seconds is None:
        return None
    s = round(seconds)
    return f"{s // 60}'{s % 60:02d}\""


def _get(section: dict[str, Any], *keys: str) -> Any:
    value: Any = section
    for key in keys:
        if not isinstance(value, dict):
            return None
        value = value.get(key)
    return value


def _pct(value: float | None) -> float | None:
    return None if value is None else round(value * 100, 1)


def writer_brief(result: SessionResultOut) -> dict[str, Any]:
    """writer가 받는 입력. 키는 camelCase, 없는 값은 빼지 않고 None으로 둔다(모델이 '없음'을 배우게)."""
    s, m, d = result.summary, result.metrics, result.coaching.detail
    split = d.get("split", {})
    brief: dict[str, Any] = {
        "run": {
            "distanceKm": round(s.distanceMeters / 1000, 2),
            "durationMin": round(s.activeMs / 60000),
            "pace": pace_text(s.paceSecondsPerKm),
            "avgHeartRate": s.avgHeartRate,
            "maxHeartRate": s.maxHeartRate,
            "cadence": s.cadenceMedian,
            "recovery1m": s.recoveryHeartRate1m,
        },
        "axes": {name: {"score": a.score, "label": a.label} for name, a in result.axes.items() if name in _AXES},
        "flags": list(result.coaching.flags),
        "facts": {
            "hrMax": _get(m, "intensity", "hrMaxUsed"),
            "z4PlusPct": _get(m, "intensity", "z4PlusPct"),
            "efficiencyChangePct": _pct(_get(m, "endurance", "efficiencyChange")),
            "paceVariationPct": _pct(_get(m, "stability", "paceCv")),
            "z4ReachedAtSec": _get(d, "warmup", "z4ReachedAtS"),
            "first500mPace": pace_text(_get(d, "warmup", "first500mPaceS")),
            "splitType": split.get("type"),
            "firstHalfPace": pace_text(split.get("firstHalfPaceS")),
            "secondHalfPace": pace_text(split.get("secondHalfPaceS")),
            "strideM": _get(d, "overstride", "strideM"),
            "recoveryBaseline1m": _get(d, "recovery", "baseline1m"),
            "acwrRatio": _get(d, "acwr", "ratio"),
        },
        "prev": (
            {
                "distanceKm": round(result.prev.distanceMeters / 1000, 2),
                "pace": pace_text(result.prev.paceSecondsPerKm),
                "recovery1m": result.prev.recoveryHeartRate1m,
            }
            if result.prev
            else None
        ),
        "rule": {"fix": result.feedback.ruleId, "good": result.feedback.goodRuleId},
        "template": {
            "headline": result.feedback.headline,
            "good": result.feedback.good,
            "fix": result.feedback.fix,
            "next": result.feedback.next,
        },
    }
    return brief
