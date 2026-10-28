"""상황 만들기: 변형이 엔진 입력으로 유효하고, 손잡이가 의도한 규칙을 낸다. 같은 seed면 같은 결과."""

from __future__ import annotations

import itertools
import random
from dataclasses import replace
from typing import Any

from runkk_ai.runbody import analyze_session
from runkk_ai.writer.dataset.scenarios import Knobs, balanced, generate, sample_knobs, transform

PLAIN = Knobs(
    base=1,
    birth_year=2000,
    pace_factor=1.0,
    split_slope=0.0,
    start_mode="plain",
    start_seconds=120,
    hr_scale=1.0,
    hr_offset=0.0,
    cadence_mode="keep",
    cadence_factor=1.0,
    keep_fraction=1.0,
    recovery_factor=1.0,
    history="none",
    prev_distance_factor=1.0,
    prev_recovery_shift=0,
    acwr_ratio=None,
)


def test_identity_knobs_keep_the_real_run(bases: list[dict[str, Any]]) -> None:
    session = transform(bases[1], PLAIN, "s", "u")

    assert abs(session.distanceMeters - bases[1]["distanceMeters"]) < 1
    assert session.activeMs == bases[1]["activeMs"]


def test_shorter_and_faster_run(bases: list[dict[str, Any]]) -> None:
    session = transform(bases[1], replace(PLAIN, keep_fraction=0.5, pace_factor=1.2), "s", "u")

    assert session.activeMs == bases[1]["activeMs"] // 2
    assert session.distanceMeters < bases[1]["distanceMeters"]


def test_strong_negative_slope_slows_second_half(bases: list[dict[str, Any]]) -> None:
    result = analyze_session(transform(bases[1], replace(PLAIN, split_slope=-0.3, start_mode="gentle"), "s", "u"), [])

    assert result.coaching.detail["split"]["type"] == "positive"


def test_dropped_cadence_has_no_form_score(bases: list[dict[str, Any]]) -> None:
    result = analyze_session(transform(bases[0], replace(PLAIN, base=0, cadence_mode="drop"), "s", "u"), [])

    assert result.summary.cadenceMedian is None


def test_same_seed_same_situations(bases: list[dict[str, Any]]) -> None:
    first = [s.result.feedback for s in itertools.islice(generate(bases, 3), 3)]
    again = [s.result.feedback for s in itertools.islice(generate(bases, 3), 3)]

    assert first == again
    assert sample_knobs(random.Random(1)) == sample_knobs(random.Random(1))


def test_history_and_load_reach_their_rules(bases: list[dict[str, Any]]) -> None:
    rules = {s.result.feedback.ruleId for s in itertools.islice(generate(bases, 11), 120)}

    assert {"overpace_start", "positive_split"} <= rules


def test_balanced_caps_each_rule(bases: list[dict[str, Any]]) -> None:
    picked = balanced(bases, total=16, seed=5, max_tries=60)

    per_rule: dict[str, int] = {}
    for s in picked:
        per_rule[s.result.feedback.ruleId] = per_rule.get(s.result.feedback.ruleId, 0) + 1
    assert 0 < len(picked) <= 16
    assert max(per_rule.values()) <= 2
    assert picked[0].record()["situationId"].startswith("syn-5-")
