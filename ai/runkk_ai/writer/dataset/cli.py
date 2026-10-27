"""writer 학습 데이터 만들기 명령(S15P31A207-191). ai 폴더에서 실행한다.

1) 상황:  uv run python -m runkk_ai.writer.dataset situations --total 1000
2) 초안:  uv run python -m runkk_ai.writer.dataset draft --model claude-opus-5-5
          (RUNKK_ANTHROPIC_API_KEY, ANTHROPIC_BASE_URL=GMS 주소 필요. 중간에 끊겨도 다시 돌리면 이어서 한다)
3) 검사:  uv run python -m runkk_ai.writer.dataset check --eval 100
          → train.jsonl(학습), eval.jsonl(홀드아웃), report.json(탈락 이유·규칙별 개수)

결과는 data/writer/에 쌓인다(git에 올리지 않는다).
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import random
import sys
import time
from collections import Counter, defaultdict
from collections.abc import Awaitable, Callable, Iterable
from pathlib import Path
from typing import Any

from runkk_ai.writer.brief import BRIEF_VERSION, writer_brief
from runkk_ai.writer.checks import check
from runkk_ai.writer.dataset.scenarios import balanced
from runkk_ai.writer.prompt import PROMPT_VERSION, SYSTEM, user_message

DATA = Path("data/writer")
FIXTURES = Path("tests/fixtures")
DEFAULT_MODEL = "claude-opus-5-5"
Teacher = Callable[[str, str], Awaitable[tuple[str, dict[str, int]]]]


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    n = 0
    with path.open("w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
            n += 1
    return n


# ---------- 1) 상황 ----------
def build_situations(total: int, seed: int, fixtures: Path = FIXTURES) -> list[dict[str, Any]]:
    bases = [json.loads((fixtures / f"{n}.json").read_text(encoding="utf-8")) for n in ("first_run", "second_run")]
    rows = []
    for s in balanced(bases, total, seed):
        brief = writer_brief(s.result)
        rows.append(
            {
                "situationId": s.situation_id,
                "briefVersion": BRIEF_VERSION,
                "rule": brief["rule"],
                "brief": brief,
            }
        )
    return rows


# ---------- 2) 초안 ----------
def anthropic_teacher(model: str, max_tokens: int = 600) -> Teacher:
    import anthropic  # 초안 단계에서만 쓴다

    key = os.environ.get("RUNKK_ANTHROPIC_API_KEY") or os.environ.get("ANTHROPIC_API_KEY")
    if not key:
        raise SystemExit("RUNKK_ANTHROPIC_API_KEY(GMS 키)와 ANTHROPIC_BASE_URL(GMS 주소)을 넣고 실행한다")
    client = anthropic.AsyncAnthropic(api_key=key, timeout=60, max_retries=3)

    async def ask(system: str, user: str) -> tuple[str, dict[str, int]]:
        message = await client.messages.create(
            model=model,
            max_tokens=max_tokens,
            system=system,
            messages=[{"role": "user", "content": user}],
        )
        text = "".join(block.text for block in message.content if block.type == "text")
        return text, {"input": message.usage.input_tokens, "output": message.usage.output_tokens}

    return ask


async def draft(situations: list[dict[str, Any]], out: Path, teacher: Teacher, model: str, concurrency: int = 4) -> int:
    """아직 초안이 없는 상황만 묻고 한 줄씩 덧붙인다(끊겨도 이어서 한다)."""
    done = {row["situationId"] for row in read_jsonl(out)}
    todo = [s for s in situations if s["situationId"] not in done]
    out.parent.mkdir(parents=True, exist_ok=True)
    semaphore = asyncio.Semaphore(concurrency)
    lock = asyncio.Lock()
    written = 0

    async def one(situation: dict[str, Any]) -> None:
        nonlocal written
        async with semaphore:
            try:
                raw, usage = await teacher(SYSTEM, user_message(situation["brief"]))
            except Exception as exc:  # 한 건 실패로 전체를 멈추지 않는다. 다시 돌리면 이 건만 묻는다
                print(f"실패 {situation['situationId']}: {type(exc).__name__}", file=sys.stderr)
                return
        row = {
            "situationId": situation["situationId"],
            "model": model,
            "promptVersion": PROMPT_VERSION,
            "raw": raw,
            "usage": usage,
            "at": int(time.time()),
        }
        async with lock:
            with out.open("a", encoding="utf-8") as f:
                f.write(json.dumps(row, ensure_ascii=False) + "\n")
            written += 1
            if written % 50 == 0:
                print(f"{written}/{len(todo)}", file=sys.stderr)

    await asyncio.gather(*(one(s) for s in todo))
    return written


# ---------- 3) 검사·나누기 ----------
def training_row(brief: dict[str, Any], output: dict[str, str]) -> dict[str, Any]:
    return {
        "messages": [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": user_message(brief)},
            {"role": "assistant", "content": json.dumps(output, ensure_ascii=False)},
        ]
    }


def check_and_split(
    situations: list[dict[str, Any]], drafts: list[dict[str, Any]], eval_count: int, seed: int
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, Any]]:
    """검사 통과분을 규칙별로 고르게 홀드아웃(eval)과 학습(train)으로 나눈다. 같은 문장 쌍은 하나만 남긴다."""
    by_id = {s["situationId"]: s for s in situations}
    reasons: Counter[str] = Counter()
    seen: set[tuple[str, str]] = set()
    passed: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for d in drafts:
        situation = by_id.get(d["situationId"])
        if situation is None:
            continue
        result = check(d["raw"], situation["brief"])
        if not result.ok or result.output is None:
            reasons.update(r.split(":")[0] for r in result.reasons)
            continue
        key = (result.output["headline"], result.output["fix"])
        if key in seen:
            reasons["duplicate"] += 1
            continue
        seen.add(key)
        passed[situation["rule"]["fix"]].append(
            {
                "situationId": d["situationId"],
                "rule": situation["rule"],
                "brief": situation["brief"],
                "output": result.output,
            }
        )
    rng = random.Random(seed)
    train: list[dict[str, Any]] = []
    holdout: list[dict[str, Any]] = []
    per_rule = -(-eval_count // max(len(passed), 1))
    for rule in sorted(passed):
        rows = passed[rule]
        rng.shuffle(rows)
        holdout.extend(rows[:per_rule])
        train.extend(rows[per_rule:])
    rng.shuffle(train)
    overflow = holdout[eval_count:]
    holdout, train = holdout[:eval_count], train + overflow
    report = {
        "drafts": len(drafts),
        "passed": sum(len(v) for v in passed.values()),
        "rejected": dict(reasons),
        "train": len(train),
        "eval": len(holdout),
        "trainByRule": dict(Counter(r["rule"]["fix"] for r in train)),
        "promptVersion": PROMPT_VERSION,
        "briefVersion": BRIEF_VERSION,
    }
    return train, holdout, report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m runkk_ai.writer.dataset")
    parser.add_argument("--data", type=Path, default=DATA)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("situations", help="상황 만들기")
    p.add_argument("--total", type=int, default=1000)
    p.add_argument("--seed", type=int, default=7)
    p = sub.add_parser("draft", help="API 모델로 문장 초안")
    p.add_argument("--model", default=DEFAULT_MODEL)
    p.add_argument("--concurrency", type=int, default=4)
    p.add_argument("--limit", type=int, default=0, help="앞에서 몇 건만(0이면 전부)")
    p = sub.add_parser("check", help="자동 검사 후 train·eval 나누기")
    p.add_argument("--eval", type=int, default=100)
    p.add_argument("--seed", type=int, default=7)
    args = parser.parse_args(argv)

    situations_path = args.data / "situations.jsonl"
    drafts_path = args.data / "drafts.jsonl"
    if args.command == "situations":
        n = write_jsonl(situations_path, build_situations(args.total, args.seed))
        rules = Counter(r["rule"]["fix"] for r in read_jsonl(situations_path))
        print(f"{n}건 → {situations_path}\n{dict(rules)}")
    elif args.command == "draft":
        situations = read_jsonl(situations_path)
        if args.limit:
            situations = situations[: args.limit]
        n = asyncio.run(draft(situations, drafts_path, anthropic_teacher(args.model), args.model, args.concurrency))
        print(f"새 초안 {n}건 → {drafts_path}")
    else:
        train, holdout, report = check_and_split(
            read_jsonl(situations_path), read_jsonl(drafts_path), args.eval, args.seed
        )
        write_jsonl(args.data / "train.jsonl", (training_row(r["brief"], r["output"]) for r in train))
        write_jsonl(args.data / "eval.jsonl", holdout)
        (args.data / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0
