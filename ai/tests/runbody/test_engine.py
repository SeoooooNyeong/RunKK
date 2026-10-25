"""RUN바디 1회 분석 회귀 시험. 실측 러닝 2회(10/18, 10/19)의 결과 숫자와 판정 문구를 고정한다."""

from __future__ import annotations

import pytest

from runkk_ai.runbody import SessionIn, SessionResultOut, analyze_session
from tests.runbody.conftest import derive


def test_first_run_summary_and_metrics(first_run: SessionIn) -> None:
    result = analyze_session(first_run, history=[])

    s, m = result.summary, result.metrics
    assert (s.distanceMeters, s.paceSecondsPerKm, s.avgHeartRate, s.maxHeartRate) == (2027, 379, 150, 165)
    assert s.cadenceMedian == 166
    assert s.recoveryHeartRate1m == -14
    assert abs(m["intensity"]["hrZonePct"]["z4"] - 45) < 1.5
    assert 7 <= m["endurance"]["hrDriftBpm"] <= 11
    assert m["form"]["strideM"] == pytest.approx(0.98, abs=0.03)
    assert m["course"]["gpsLockDelayS"] == 148
    assert m["course"]["gpsJumps"] == 0
    assert result.laps[0].paceSeconds == 378


def test_first_run_axes_and_labels(first_run: SessionIn) -> None:
    axes = analyze_session(first_run, history=[]).axes

    assert {k: a.score for k, a in axes.items()} == {
        "intensity": 72,
        "endurance": 50,
        "stability": 80,
        "form": 65,
        "fitness": 76,
    }
    assert (axes["intensity"].label, axes["intensity"].level) == ("높음", "warn")
    assert (axes["stability"].label, axes["stability"].level) == ("일정", "good")
    assert (axes["form"].label, axes["form"].level) == ("양호", "good")
    assert (axes["fitness"].label, axes["fitness"].level) == ("보통", "mid")


def test_first_run_feedback_is_too_hard(first_run: SessionIn) -> None:
    result = analyze_session(first_run, history=[])

    assert result.coaching.flags == []
    assert result.feedback.headline == "쉬운 날이 아니었어요"
    assert result.feedback.ruleId == "too_hard"
    assert result.feedback.source == "template"
    assert result.prev is None
    assert result.qualityFlags == ["gps_lock_slow", "pre_gps_distance_estimated"]


def test_second_run_flags_and_feedback(first_run: SessionIn, second_run: SessionIn) -> None:
    first = analyze_session(first_run, history=[])

    result = analyze_session(second_run, history=[first])

    assert result.coaching.flags == ["warmup_missing", "positive_split", "overstride"]
    assert result.feedback.headline == "출발이 너무 빨랐어요"
    assert result.feedback.ruleId == "overpace_start"
    assert result.feedback.good == "회복 심박이 좋아졌어요 (1분 -27, 지난번 -14)"
    assert result.prev is not None
    assert (result.prev.sessionId, result.prev.recoveryHeartRate1m) == ("run-20261018", -14)
    assert result.axes["form"].label == "보폭이 긴 편"
    assert result.axes["endurance"].level == "warn"


def test_no_heart_rate_leaves_hr_axes_empty(second_run: SessionIn) -> None:
    session = derive(second_run, drop_heart_rate=True)

    result = analyze_session(session, history=[])

    assert result.axes["intensity"].score is None
    assert result.axes["intensity"].level == "none"
    assert result.axes["endurance"].score is None
    assert result.summary.avgHeartRate is None
    assert "no_heart_rate" in result.qualityFlags
    assert all(lap.avgHeartRate is None for lap in result.laps)


def test_no_gps_uses_distance_meters(second_run: SessionIn) -> None:
    result = analyze_session(derive(second_run, drop_gps=True), history=[])

    assert result.summary.distanceMeters == 3008
    assert result.metrics["course"] == {"gps": False}
    assert "no_gps" in result.qualityFlags


def test_no_cadence_skips_overstride(second_run: SessionIn) -> None:
    result = analyze_session(derive(second_run, drop_cadence=True), history=[])

    assert "overstride" not in result.coaching.flags
    assert result.coaching.detail["overstride"] == {"flag": False, "skipped": "no_cadence"}
    assert result.axes["form"].score is None
    assert result.axes["form"].label == "케이던스 없음"
    assert "no_cadence" in result.qualityFlags


def test_recovery_slower_needs_three_previous_runs(first_run: SessionIn, second_run: SessionIn) -> None:
    """회복이 빨랐던 세 번 뒤 느린 회복이 오면 recovery_slower."""
    fast = analyze_session(second_run, history=[])  # 1분 -27
    history: list[SessionResultOut] = [fast, fast, fast]

    result = analyze_session(first_run, history=history)  # 1분 -14 → 13 bpm 둔화

    assert "recovery_slower" in result.coaching.flags
    assert result.coaching.detail["recovery"]["delta"] == 13.0


def test_short_run_flag_and_finished_feedback(second_run: SessionIn) -> None:
    session = derive(second_run, max_seconds=200)

    result = analyze_session(session, history=[])

    assert "short_run" in result.qualityFlags
    assert result.feedback.headline
    assert result.axes["endurance"].score is not None or result.axes["endurance"].level == "none"


def test_result_is_json_safe(second_run: SessionIn) -> None:
    """NaN이 응답에 섞이면 JSON 직렬화가 실패한다. 심박이 없어도 직렬화된다."""
    result = analyze_session(derive(second_run, drop_heart_rate=True), history=[])

    text = result.model_dump_json()

    assert "NaN" not in text
    assert "Infinity" not in text
