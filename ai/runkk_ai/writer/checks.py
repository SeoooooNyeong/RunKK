"""writer 출력 자동 검사. 학습 데이터 거르기와 평가(규칙 준수율, S15P31A207-195)가 같이 쓴다.

통과 기준
- JSON 하나, 키 4개(headline·good·fix·next), 빈 값 없음, 길이 상한
- 숫자: 출력의 모든 숫자가 brief(템플릿 문장 포함)에 있는 숫자여야 한다(숫자 지어내기 방지)
- 금칙어·이모지 없음
- all_good이 아닌데 고칠 점이 '없다'고 하지 않음
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from typing import Any

KEYS = ("headline", "good", "fix", "next")
MAX_LEN = {"headline": 25, "good": 120, "fix": 120, "next": 120}
FORBIDDEN = ("위험", "큰일", "병원", "진단", "부상을 입", "부상입", "질환", "반드시", "절대", "최악")
# 단위·순서처럼 요약에 없어도 자연스러운 작은 수(1주, 하루, 2번째 등)
FREE_NUMBERS = frozenset({0.0, 1.0, 2.0})
_NUMBER = re.compile(r"\d+(?:\.\d+)?")
_EMOJI = re.compile("[\U0001f300-\U0001faff☀-➿]")
_NO_FIX = re.compile(r"고칠 점(?:이|은)? (?:없|딱히 없)")


@dataclass
class CheckResult:
    ok: bool
    reasons: list[str] = field(default_factory=list)
    output: dict[str, str] | None = None


def numbers_in(text: str) -> set[float]:
    return {float(n) for n in _NUMBER.findall(text)}


def allowed_numbers(brief: dict[str, Any]) -> set[float]:
    return numbers_in(json.dumps(brief, ensure_ascii=False)) | FREE_NUMBERS


def parse_output(raw: str) -> dict[str, Any] | None:
    """앞뒤 군더더기(```json 등)를 걷어내고 JSON 객체 하나를 읽는다."""
    start, end = raw.find("{"), raw.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        value = json.loads(raw[start : end + 1])
    except json.JSONDecodeError:
        return None
    return value if isinstance(value, dict) else None


def check(raw: str, brief: dict[str, Any]) -> CheckResult:
    data = parse_output(raw)
    if data is None:
        return CheckResult(False, ["not_json"])
    reasons: list[str] = []
    if set(data) != set(KEYS):
        reasons.append("keys")
    out = {k: str(data.get(k) or "").strip() for k in KEYS}
    allowed = allowed_numbers(brief)
    for key in KEYS:
        text = out[key]
        if not text:
            reasons.append(f"empty:{key}")
            continue
        if len(text) > MAX_LEN[key]:
            reasons.append(f"too_long:{key}")
        if extra := numbers_in(text) - allowed:
            reasons.append(f"number:{key}:{','.join(sorted(f'{n:g}' for n in extra))}")
        if any(word in text for word in FORBIDDEN):
            reasons.append(f"forbidden:{key}")
        if _EMOJI.search(text):
            reasons.append(f"emoji:{key}")
    if brief.get("rule", {}).get("fix") != "all_good" and _NO_FIX.search(out["fix"]):
        reasons.append("fix_denied")
    return CheckResult(not reasons, reasons, out)
