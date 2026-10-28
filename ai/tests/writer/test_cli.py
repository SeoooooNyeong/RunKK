"""학습 데이터 명령: 초안은 끊겨도 이어서 하고, 검사 통과분만 규칙별로 나눠 학습·평가 파일을 만든다."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pytest

from runkk_ai.writer.dataset import cli


def _situation(i: int, rule: str, brief: dict[str, Any]) -> dict[str, Any]:
    return {"situationId": f"s{i}", "briefVersion": 1, "rule": {**brief["rule"], "fix": rule}, "brief": brief}


def _good_raw(i: int) -> str:
    return json.dumps(
        {"headline": f"판정 {i % 2}", "good": "좋아요", "fix": f"고칠 점 문장 {i % 2}번째", "next": "천천히 시작해요"},
        ensure_ascii=False,
    )


async def test_draft_resumes_and_skips_failures(tmp_path: Path, brief: dict[str, Any]) -> None:
    situations = [_situation(i, "overpace_start", brief) for i in range(4)]
    out = tmp_path / "drafts.jsonl"
    asked: list[str] = []

    async def teacher(system: str, user: str) -> tuple[str, dict[str, int]]:
        asked.append(user)
        if len(asked) == 2:
            raise TimeoutError
        return "{}", {"input": 1, "output": 1}

    first = await cli.draft(situations, out, teacher, "m", concurrency=1)
    second = await cli.draft(situations, out, teacher, "m", concurrency=1)

    assert (first, second) == (3, 1)
    assert len(cli.read_jsonl(out)) == 4
    assert "분석 요약" in asked[0]


def test_check_and_split(brief: dict[str, Any]) -> None:
    situations = [_situation(i, rule, brief) for i, rule in enumerate(["a", "a", "b", "b", "b", "a"])]
    drafts = [{"situationId": f"s{i}", "raw": _good_raw(i)} for i in range(4)]
    drafts += [{"situationId": "s4", "raw": "not json"}, {"situationId": "s5", "raw": _good_raw(5)}]
    drafts += [{"situationId": "ghost", "raw": _good_raw(9)}]

    train, holdout, report = cli.check_and_split(situations, drafts, eval_count=1, seed=1)

    assert report["rejected"] == {"not_json": 1, "duplicate": 3}
    assert len(holdout) == 1
    assert len(train) + len(holdout) == report["passed"] == 2
    row = cli.training_row(train[0]["brief"], train[0]["output"])
    assert [m["role"] for m in row["messages"]] == ["system", "user", "assistant"]


def test_main_situations_and_check(tmp_path: Path, monkeypatch: pytest.MonkeyPatch, brief: dict[str, Any]) -> None:
    monkeypatch.setattr(cli, "build_situations", lambda total, seed: [_situation(0, "a", brief)])
    assert cli.main(["--data", str(tmp_path), "situations", "--total", "1"]) == 0
    cli.write_jsonl(tmp_path / "drafts.jsonl", [{"situationId": "s0", "raw": _good_raw(0)}])

    assert cli.main(["--data", str(tmp_path), "check", "--eval", "0"]) == 0
    assert len(cli.read_jsonl(tmp_path / "train.jsonl")) == 1
    assert json.loads((tmp_path / "report.json").read_text(encoding="utf-8"))["train"] == 1


def test_draft_without_key_stops(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("RUNKK_ANTHROPIC_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)

    with pytest.raises(SystemExit):
        cli.anthropic_teacher("m")


def test_build_situations_small(tmp_path: Path) -> None:
    rows = cli.build_situations(total=8, seed=2)

    assert 0 < len(rows) <= 8
    assert {"situationId", "brief", "rule"} <= set(rows[0])
