"""피드백 템플릿 규칙 시험(F3, AI-15). 고칠 점 규칙 8개와 좋은 점 규칙 7개, 우선순위를 고정한다.

고칠 점 우선순위: 출발 과속 > 예열 없음 > 후반 처짐 > 부하 급증 > 회복 둔화 > 강도 과다 > 보폭 과다 > 무난.
판정(headline)은 가장 앞선 고칠 점이고, 다음 두 개는 moreFixes로 간다.
"""

from __future__ import annotations

from typing import Any

import pytest

from runkk_ai.runbody.feedback import FIX_RULES, GOOD_RULES, FeedbackInput, compose
from runkk_ai.runbody.schemas import CoachingFlag

EVEN = {"type": "even", "first_half_pace_s": 360, "second_half_pace_s": 362}
POSITIVE = {"type": "positive", "first_half_pace_s": 336, "second_half_pace_s": 398}
NEGATIVE = {"type": "negative", "first_half_pace_s": 380, "second_half_pace_s": 350}


def _input(
    flags: tuple[CoachingFlag, ...] = (),
    *,
    split: dict[str, Any] | None = None,
    z4: float = 20.0,
    cv: float = 0.10,
    efficiency: float = -0.08,
    recovery: int | None = -8,
    prev_recovery: int | None = None,
    prev_distance: int | None = None,
    distance: int = 3000,
) -> FeedbackInput:
    return FeedbackInput(
        distance_m=distance,
        pace_s_per_km=361,
        metrics={
            "intensity": {"hr_max_used": 194, "z4_plus_pct": z4},
            "endurance": {"efficiency_change": efficiency},
            "stability": {"pace_cv": cv},
            "fitness": {"recovery_hr_1m": recovery},
        },
        flags=flags,
        detail={
            "warmup": {"z4_reached_at_s": 122, "first_500m_pace_s": 317},
            "split": split or EVEN,
            "overstride": {"flag": "overstride" in flags, "cadence": 165, "stride_m": 1.02},
            "recovery": {"flag": "recovery_slower" in flags, "current_1m": -6, "baseline_1m": -20.0},
            "acwr": {"flag": "acwr_high" in flags, "ratio": 2.0},
        },
        prev_distance_m=prev_distance,
        prev_recovery_1m=prev_recovery,
    )


@pytest.mark.parametrize(
    ("x", "rule", "headline"),
    [
        (_input(("warmup_missing", "positive_split"), split=POSITIVE), "overpace_start", "출발이 너무 빨랐어요"),
        (_input(("warmup_missing",)), "no_warmup", "예열 없이 바로 달렸어요"),
        (_input(("positive_split",), split=POSITIVE), "positive_split", "후반에 페이스가 처졌어요"),
        (_input(("acwr_high",)), "acwr_high", "이번 주 부하가 급증했어요"),
        (_input(("recovery_slower",)), "recovery_slower", "회복이 평소보다 느렸어요"),
        (_input(z4=45.0), "too_hard", "쉬운 날이 아니었어요"),
        (_input(("overstride",)), "overstride", "보폭이 긴 편이에요"),
        (_input(), "all_good", "무난한 러닝이었어요"),
    ],
    ids=lambda v: v if isinstance(v, str) else "",
)
def test_each_fix_rule(x: FeedbackInput, rule: str, headline: str) -> None:
    feedback = compose(x)

    assert (feedback.ruleId, feedback.headline) == (rule, headline)
    assert feedback.fix
    assert feedback.next


def test_fix_rules_are_the_eight_documented() -> None:
    assert FIX_RULES == (
        "overpace_start",
        "no_warmup",
        "positive_split",
        "acwr_high",
        "recovery_slower",
        "too_hard",
        "overstride",
        "all_good",
    )


def test_overpace_start_hides_too_hard_and_lists_more_fixes() -> None:
    """출발 과속이면 강도 과다는 같은 원인이라 빼고, 나머지 고칠 점은 moreFixes에 최대 2개."""
    x = _input(("warmup_missing", "positive_split", "acwr_high", "overstride"), split=POSITIVE, z4=50.0)

    feedback = compose(x)

    assert feedback.ruleId == "overpace_start"
    assert feedback.moreFixes == [
        "최근 7일 거리가 평소 주간의 2.0배예요 (기준 1.5 이하)",
        "케이던스 165 spm에 보폭 1.02 m. 발이 몸보다 앞에 닿아 무릎 충격이 커져요",
    ]


def test_no_warmup_without_z4_uses_first_500m() -> None:
    x = _input(("warmup_missing",))
    x.detail["warmup"]["z4_reached_at_s"] = None

    assert compose(x).fix == "첫 500 m를 평균보다 빠른 5'17\"로 나갔어요"


@pytest.mark.parametrize(
    ("x", "rule"),
    [
        (_input(recovery=-27, prev_recovery=-14), "recovery_improved"),
        (_input(split=NEGATIVE), "negative_split"),
        (_input(cv=0.05), "steady_pace"),
        (_input(recovery=-15), "recovery_good"),
        (_input(efficiency=-0.02), "efficiency_kept"),
        (_input(distance=3000, prev_distance=2000), "distance_up"),
        (_input(), "finished"),
    ],
    ids=lambda v: v if isinstance(v, str) else "",
)
def test_each_good_rule(x: FeedbackInput, rule: str) -> None:
    assert compose(x).goodRuleId == rule


def test_good_rules_are_the_seven_documented() -> None:
    assert GOOD_RULES == (
        "recovery_improved",
        "negative_split",
        "steady_pace",
        "recovery_good",
        "efficiency_kept",
        "distance_up",
        "finished",
    )
