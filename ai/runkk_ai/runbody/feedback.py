"""러닝 피드백 문장(F3): 판정(headline) + 좋은 점 1 + 고칠 점 1 + 다음 행동 1.

템플릿 규칙만 쓴다. 코칭 플래그가 있으면 그 규칙이 먼저 온다. 자체 모델(writer)·LLM 경로는 이후 MR에서 붙인다.
같은 입력이면 항상 같은 문장이 나와 API·모델이 없어도 결과가 비지 않는다.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from runkk_ai.runbody.coaching import target_stride
from runkk_ai.runbody.schemas import CoachingFlag, FeedbackOut, Metrics

TOO_HARD_Z4_PCT = 40
STEADY_PACE_CV = 0.08
RECOVERY_GOOD_1M = -12
RECOVERY_IMPROVED_BPM = 5
EFFICIENCY_KEPT = -0.05
DISTANCE_UP_M = 500
_DEFAULT_HR_MAX = 190


@dataclass(frozen=True, slots=True)
class FeedbackInput:
    distance_m: int
    pace_s_per_km: int
    metrics: Metrics
    flags: Sequence[CoachingFlag]
    detail: Metrics
    prev_distance_m: int | None
    prev_recovery_1m: int | None


def _pace(seconds: float) -> str:
    return f"{int(seconds // 60)}'{int(seconds % 60):02d}\""


def _halves(split: Metrics, *, labeled: bool = False) -> str:
    """전반·후반 페이스. labeled면 '전반 6'05" → 후반 6'40"', 아니면 '6'05" → 6'40"'."""
    a, b = _pace(split["first_half_pace_s"]), _pace(split["second_half_pace_s"])
    return f"전반 {a} → 후반 {b}" if labeled else f"{a} → {b}"


def _goods(x: FeedbackInput) -> list[tuple[str, str]]:
    """좋은 점 후보(우선순위 순). 항상 하나 이상 나온다."""
    fitness, stability, endurance = (x.metrics.get(k, {}) for k in ("fitness", "stability", "endurance"))
    sp = x.detail["split"]
    out: list[tuple[str, str]] = []
    r1 = fitness.get("recovery_hr_1m")
    if r1 is not None and x.prev_recovery_1m is not None and r1 <= x.prev_recovery_1m - RECOVERY_IMPROVED_BPM:
        out.append((f"회복 심박이 좋아졌어요 (1분 {r1}, 지난번 {x.prev_recovery_1m})", "recovery_improved"))
    if sp.get("type") == "negative":
        text = f"후반이 더 빨랐어요 ({_halves(sp)}). 이상적인 배분이에요"
        out.append((text, "negative_split"))
    cv = stability.get("pace_cv")
    if cv is not None and cv < STEADY_PACE_CV:
        out.append((f"페이스를 일정하게 지켰어요 (변동 {cv * 100:.1f}%)", "steady_pace"))
    if r1 is not None and r1 <= RECOVERY_GOOD_1M:
        out.append((f"회복이 빨라요 (1분 {r1}, 기준 −12)", "recovery_good"))
    eff = endurance.get("efficiency_change")
    if eff is not None and eff > EFFICIENCY_KEPT:
        out.append(("후반까지 효율을 유지했어요", "efficiency_kept"))
    if x.prev_distance_m is not None and x.distance_m > x.prev_distance_m + DISTANCE_UP_M:
        text = f"거리를 {x.prev_distance_m / 1000:.1f} → {x.distance_m / 1000:.1f} km로 늘렸어요"
        out.append((text, "distance_up"))
    if not out:
        out.append((f"{x.distance_m / 1000:.1f} km 완주했어요", "finished"))
    return out


def _fixes(x: FeedbackInput) -> list[tuple[str, str, str, str]]:
    """고칠 점 후보(우선순위 순). (판정, 고칠 점, 다음 행동, 규칙 ID)"""
    inten, endu = x.metrics.get("intensity", {}), x.metrics.get("endurance", {})
    hr_max = int(inten.get("hr_max_used", _DEFAULT_HR_MAX))
    z2 = f"{int(hr_max * 0.6)}~{int(hr_max * 0.7)}"
    w, sp = x.detail["warmup"], x.detail["split"]
    out: list[tuple[str, str, str, str]] = []
    z4_at: int | None = w.get("z4_reached_at_s")
    first_500: int | None = w.get("first_500m_pace_s")

    if "warmup_missing" in x.flags and sp.get("type") == "positive":
        why = f"첫 500 m를 {_pace(first_500)}로 나갔고" if first_500 else "출발 직후 급가속해서"
        at = f" {z4_at // 60}분 {z4_at % 60}초 만에 역치 구간에 들어갔어요" if z4_at is not None else ""
        out.append(
            (
                "출발이 너무 빨랐어요",
                f"{why}{at}. 그래서 {_halves(sp, labeled=True)}로 느려졌어요",
                f"첫 1 km를 {_pace(x.pace_s_per_km + 10)}보다 느리게 묶어 보세요. 그러면 후반이 더 빨라질 수 있어요",
                "overpace_start",
            )
        )
    elif "warmup_missing" in x.flags:
        if z4_at is not None:
            fix = f"출발 {z4_at // 60}분 {z4_at % 60}초 만에 심박이 역치 구간까지 올라갔어요"
        else:
            fix = f"첫 500 m를 평균보다 빠른 {_pace(first_500 or 0)}로 나갔어요"
        out.append(
            (
                "예열 없이 바로 달렸어요",
                fix,
                "처음 3분은 걷기나 아주 느린 조깅으로 심박을 천천히 올려 보세요",
                "no_warmup",
            )
        )
    elif sp.get("type") == "positive":
        drop = abs(float(endu.get("efficiency_change", 0.0))) * 100
        out.append(
            (
                "후반에 페이스가 처졌어요",
                f"{_halves(sp, labeled=True)}, 효율이 {drop:.0f}% 떨어졌어요",
                f"전반을 {_pace(sp['first_half_pace_s'] + 15)} 정도로 늦춰 후반에 힘을 남겨 보세요",
                "positive_split",
            )
        )
    if "acwr_high" in x.flags:
        out.append(
            (
                "이번 주 부하가 급증했어요",
                f"최근 7일 거리가 평소 주간의 {x.detail['acwr']['ratio']}배예요 (기준 1.5 이하)",
                "이번 주 남은 러닝은 쉬운 강도로 짧게, 또는 휴식",
                "acwr_high",
            )
        )
    if "recovery_slower" in x.flags:
        r = x.detail["recovery"]
        out.append(
            (
                "회복이 평소보다 느렸어요",
                f"1분 회복량 {r['current_1m']} (최근 평균 {r['baseline_1m']})",
                "오늘은 여기까지. 다음 러닝 전 하루는 쉬거나 스트레칭만",
                "recovery_slower",
            )
        )
    z4_pct = float(inten.get("z4_plus_pct", 0.0))
    if z4_pct >= TOO_HARD_Z4_PCT and not any(o[3] == "overpace_start" for o in out):
        out.append(
            (
                "쉬운 날이 아니었어요",
                f"시간의 {z4_pct:.0f}%를 역치 구간(Z4+) 이상에서 뛰었어요 (쉬운 러닝 기준 30% 이하)",
                f"쉬운 날은 심박 {z2}(Z2)에 머물도록 {_pace(x.pace_s_per_km + 60)} 근처로 늦춰 보세요",
                "too_hard",
            )
        )
    if "overstride" in x.flags:
        o = x.detail["overstride"]
        target = target_stride(1000 / x.pace_s_per_km)
        shorter_cm = round((o["stride_m"] - target) * 100)
        out.append(
            (
                "보폭이 긴 편이에요",
                f"케이던스 {o['cadence']} spm에 보폭 {o['stride_m']} m. 발이 몸보다 앞에 닿아 무릎 충격이 커져요",
                f"170 bpm 음악에 발을 맞춰 보세요. 같은 속도면 보폭이 {target} m로 {shorter_cm} cm 줄어요",
                "overstride",
            )
        )
    if not out:
        out.append(("무난한 러닝이었어요", "특별히 고칠 점이 없어요", "다음엔 거리를 10% 늘려 보세요", "all_good"))
    return out


def compose(x: FeedbackInput) -> FeedbackOut:
    goods, fixes = _goods(x), _fixes(x)
    headline, fix, next_action, rule_id = fixes[0]
    good, good_rule = goods[0]
    return FeedbackOut(
        headline=headline,
        good=good,
        fix=fix,
        next=next_action,
        moreFixes=[f[1] for f in fixes[1:3]],
        ruleId=rule_id,
        goodRuleId=good_rule,
    )
