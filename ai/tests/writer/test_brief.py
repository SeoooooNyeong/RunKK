"""brief: 분석 결과를 writer 입력으로 줄인다. 페이스는 사람이 읽는 형식으로 미리 바꾼다."""

from __future__ import annotations

from typing import Any

from runkk_ai.runbody import SessionResultOut
from runkk_ai.writer.brief import pace_text, writer_brief


def test_pace_text() -> None:
    assert pace_text(365) == "6'05\""
    assert pace_text(59.6) == "1'00\""
    assert pace_text(None) is None


def test_brief_of_real_second_run(second: SessionResultOut, brief: dict[str, Any]) -> None:
    assert brief["run"]["distanceKm"] == 3.01
    assert brief["run"]["pace"] == pace_text(second.summary.paceSecondsPerKm)
    assert brief["rule"] == {"fix": "overpace_start", "good": second.feedback.goodRuleId}
    assert brief["template"]["headline"] == "출발이 너무 빨랐어요"
    assert brief["facts"]["splitType"] == "positive"
    assert brief["facts"]["firstHalfPace"] is not None
    assert brief["prev"] is not None
    assert set(brief["axes"]) == {"intensity", "endurance", "stability", "form", "fitness"}


def test_brief_without_previous_run(second: SessionResultOut) -> None:
    brief = writer_brief(second.model_copy(update={"prev": None}))

    assert brief["prev"] is None
