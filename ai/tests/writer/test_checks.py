"""writer 출력 검사: 형식·길이·숫자 지어내기·금칙어."""

from __future__ import annotations

import json
from typing import Any

import pytest

from runkk_ai.writer.checks import check, parse_output


def _out(**overrides: str) -> str:
    base = {
        "headline": "출발이 너무 빨랐어요",
        "good": "끝까지 완주했어요.",
        "fix": "초반에 힘을 너무 써서 후반에 느려졌어요.",
        "next": "다음엔 첫 1 km를 천천히 시작해 보세요.",
    }
    base.update(overrides)
    return json.dumps(base, ensure_ascii=False)


def test_good_output_passes(brief: dict[str, Any]) -> None:
    pace = brief["run"]["pace"]
    result = check(_out(next=f"평균 {pace} 근처로 시작해 보세요."), brief)

    assert result.ok, result.reasons


def test_code_fence_is_tolerated(brief: dict[str, Any]) -> None:
    assert check("```json\n" + _out() + "\n```", brief).ok


@pytest.mark.parametrize(
    ("raw", "reason"),
    [
        ("그냥 문장", "not_json"),
        ('{"headline": "a"}', "keys"),
        (_out(good=""), "empty:good"),
        (_out(headline="가" * 26), "too_long:headline"),
        (_out(fix="심박이 999까지 올랐어요."), "number:fix:999"),
        (_out(next="무릎이 위험할 수 있어요."), "forbidden:next"),
        (_out(good="잘했어요 🎉"), "emoji:good"),
        (_out(fix="이번엔 고칠 점이 없어요."), "fix_denied"),
    ],
)
def test_bad_outputs_are_rejected(brief: dict[str, Any], raw: str, reason: str) -> None:
    result = check(raw, brief)

    assert not result.ok
    assert reason in result.reasons


def test_all_good_may_say_nothing_to_fix(brief: dict[str, Any]) -> None:
    all_good = {**brief, "rule": {"fix": "all_good", "good": "finished"}}

    assert check(_out(fix="이번엔 고칠 점이 없어요."), all_good).ok


def test_parse_rejects_non_object() -> None:
    assert parse_output("[1, 2]") is None
    assert parse_output("{broken") is None
