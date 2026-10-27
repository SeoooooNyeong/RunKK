"""writer 학습용 러닝 상황 만들기(S15P31A207-191).

실측 러닝 2회(tests/fixtures)를 변형해 여러 상황을 만들고 RUN바디 엔진으로 분석한다.
변형 손잡이: 전체 속도, 전·후반 기울기, 출발 과속·예열, 심박 수준, 나이(최고 심박), 케이던스 유무·크기,
러닝 길이, 회복 심박, 이전 기록(지난 러닝·최근 3회), 최근 부하(ACWR).
같은 seed면 항상 같은 상황이 나온다. 고칠 점 규칙(ruleId)마다 개수를 맞춰 고른다.
"""

from __future__ import annotations

import random
from collections import Counter, defaultdict
from collections.abc import Iterator, Sequence
from dataclasses import asdict, dataclass
from datetime import timedelta
from typing import Any

from runkk_ai.runbody.engine import analyze_session
from runkk_ai.runbody.feedback import FIX_RULES
from runkk_ai.runbody.schemas import Metrics, SessionIn, SessionResultOut

HR_MIN, HR_MAX = 55.0, 215.0


@dataclass(frozen=True, slots=True)
class Knobs:
    base: int  # 실측 몇 번째(0·1)
    birth_year: int
    pace_factor: float  # 전체 속도 배율(>1이면 빠름)
    split_slope: float  # 전반→후반 속도 변화(음수면 후반이 느림)
    start_mode: str  # "fast"(출발 과속) · "gentle"(예열) · "plain"
    start_seconds: int
    hr_scale: float  # 100 bpm 기준으로 심박 폭을 늘이고 줄임
    hr_offset: float
    cadence_mode: str  # "keep" · "drop"
    cadence_factor: float
    keep_fraction: float  # 앞에서부터 남길 비율(짧은 러닝)
    recovery_factor: float  # 종료 후 심박 하락 폭 배율(클수록 회복 빠름)
    history: str  # "none" · "one" · "three"
    prev_distance_factor: float
    prev_recovery_shift: int  # 이전 기록의 1분 회복을 이번보다 몇 bpm 다르게
    acwr_ratio: float | None


def sample_knobs(rng: random.Random) -> Knobs:
    start_mode = rng.choices(["fast", "gentle", "plain"], weights=[2, 3, 4])[0]
    return Knobs(
        base=rng.randrange(2),
        birth_year=rng.randint(1968, 2008),
        pace_factor=round(rng.uniform(0.8, 1.25), 3),
        split_slope=round(
            rng.choices([rng.uniform(-0.25, -0.06), rng.uniform(-0.05, 0.05), rng.uniform(0.06, 0.2)], [2, 4, 2])[0], 3
        ),
        start_mode=start_mode,
        start_seconds=rng.randint(90, 300),
        hr_scale=round(rng.uniform(0.75, 1.12), 3),
        hr_offset=round(rng.uniform(-25, 10), 1),
        cadence_mode=rng.choices(["keep", "drop"], weights=[4, 1])[0],
        cadence_factor=round(rng.uniform(0.9, 1.14), 3),
        keep_fraction=round(rng.choice([1.0, 1.0, rng.uniform(0.45, 0.95)]), 3),
        recovery_factor=round(rng.uniform(0.3, 1.7), 3),
        history=rng.choices(["none", "one", "three"], weights=[2, 2, 4])[0],
        prev_distance_factor=round(rng.uniform(0.5, 1.4), 3),
        prev_recovery_shift=rng.randint(-14, 14),
        acwr_ratio=round(rng.uniform(0.6, 2.4), 2) if rng.random() < 0.3 else None,
    )


def _speed_factor(t: float, total: float, k: Knobs) -> float:
    progress = t / total if total else 0.0
    factor = k.pace_factor * (1 - k.split_slope / 2 + k.split_slope * progress)
    if t < k.start_seconds:
        factor *= {"fast": 1.22, "gentle": 0.72, "plain": 1.0}[k.start_mode]
    return factor


def _hr(value: float, t: float, k: Knobs) -> float:
    hr = 100 + (value - 100) * k.hr_scale + k.hr_offset
    if k.start_mode == "gentle" and t < k.start_seconds:
        hr -= 18 * (1 - t / k.start_seconds)  # 천천히 올림
    if k.start_mode == "fast" and t < k.start_seconds:
        hr += 12  # 출발부터 높음
    return min(max(hr, HR_MIN), HR_MAX)


def transform(base: dict[str, Any], k: Knobs, session_id: str, user_id: str) -> SessionIn:
    """실측 본문 → 변형한 SessionIn. 거리는 바뀐 속도로 다시 더한다."""
    active_ms = int(base["activeMs"] * k.keep_fraction)
    total_s = active_ms / 1000
    samples: list[dict[str, Any]] = []
    for s in base["samples"]:
        if s["tMs"] > active_ms:
            continue
        t = s["tMs"] / 1000
        out: dict[str, Any] = {"tMs": s["tMs"]}
        if (hr := s.get("heartRate")) is not None:
            out["heartRate"] = round(_hr(hr, t, k), 1)
        if (speed := s.get("speed")) is not None:
            f = _speed_factor(t, total_s, k)
            out["speed"] = min(speed * f, 14.9)
            out["distance"] = min((s.get("distance") or 0.0) * f, 99.0)
            if k.cadence_mode == "keep" and (cadence := s.get("cadence")) is not None:
                out["cadence"] = min(cadence * k.cadence_factor, 299.0)
        samples.append(out)
    distance = sum(x.get("distance", 0.0) for x in samples)
    recovery = base["recoveryHeartRates"]
    hr0 = recovery[0]["heartRate"]
    recovery_out = [
        {
            "tMs": r["tMs"],
            "heartRate": round(min(max(hr0 + (r["heartRate"] - hr0) * k.recovery_factor, HR_MIN), HR_MAX)),
        }
        for r in recovery
    ]
    body = {
        **{key: base[key] for key in ("source", "startedAt")},
        "sessionId": session_id,
        "userId": user_id,
        "activeMs": active_ms,
        "distanceMeters": round(max(distance, 1.0), 3),
        "profile": {"birthYear": k.birth_year},
        "samples": samples,
        "gps": [p for p in base["gps"] if p["tMs"] <= active_ms],
        "recoveryHeartRates": recovery_out,
    }
    return SessionIn.model_validate(body)


def _history(template: SessionResultOut, current: SessionIn, k: Knobs) -> list[SessionResultOut]:
    """이전 기록. 규칙이 보는 값(거리·1분 회복·시작 시각)만 바꾼 결과 사본이다."""
    count = {"none": 0, "one": 1, "three": 3}[k.history]
    current_recovery = template.summary.recoveryHeartRate1m
    out: list[SessionResultOut] = []
    for i in range(count, 0, -1):
        recovery = None if current_recovery is None else current_recovery - k.prev_recovery_shift + (i - 1)
        summary = template.summary.model_copy(
            update={
                "distanceMeters": round(current.distanceMeters * k.prev_distance_factor),
                "recoveryHeartRate1m": recovery,
                "startedAt": current.startedAt - timedelta(days=2 * i),
            }
        )
        out.append(template.model_copy(update={"sessionId": f"{current.sessionId}-prev{i}", "summary": summary}))
    return out


def _load(k: Knobs, distance_m: float) -> Metrics | None:
    if k.acwr_ratio is None:
        return None
    km_7d = distance_m / 1000 * 2
    return {"km_7d": km_7d, "km_28d": km_7d / k.acwr_ratio * 4, "days_tracked": 40}


@dataclass(frozen=True, slots=True)
class Situation:
    situation_id: str
    knobs: Knobs
    result: SessionResultOut

    def record(self) -> dict[str, Any]:
        return {
            "situationId": self.situation_id,
            "knobs": asdict(self.knobs),
            "result": self.result.model_dump(mode="json"),
        }


def generate(bases: Sequence[dict[str, Any]], seed: int) -> Iterator[Situation]:
    """끝없이 상황을 만든다. 엔진이 거부하는 변형(검증 실패)은 건너뛴다."""
    rng = random.Random(seed)
    i = 0
    while True:
        i += 1
        k = sample_knobs(rng)
        sid = f"syn-{seed}-{i:05d}"
        try:
            session = transform(bases[k.base], k, sid, f"syn-user-{i % 50:02d}")
        except ValueError:
            continue
        first = analyze_session(session, [])
        history = _history(first, session, k)
        result = (
            analyze_session(session, history, _load(k, session.distanceMeters)) if history or k.acwr_ratio else first
        )
        yield Situation(sid, k, result)


def balanced(bases: Sequence[dict[str, Any]], total: int, seed: int, max_tries: int = 40_000) -> list[Situation]:
    """고칠 점 규칙마다 거의 같은 수를 고른다. 같은 (고칠 점, 좋은 점) 조합은 한 규칙 몫의 절반까지만 받는다."""
    per_rule = -(-total // len(FIX_RULES))
    per_pair = max(per_rule // 2, 1)
    by_rule: dict[str, list[Situation]] = defaultdict(list)
    pairs: Counter[tuple[str, str]] = Counter()
    for tries, situation in enumerate(generate(bases, seed), start=1):
        fb = situation.result.feedback
        pair = (fb.ruleId, fb.goodRuleId)
        if len(by_rule[fb.ruleId]) < per_rule and pairs[pair] < per_pair:
            by_rule[fb.ruleId].append(situation)
            pairs[pair] += 1
        if sum(len(v) for v in by_rule.values()) >= per_rule * len(FIX_RULES) or tries >= max_tries:
            break
    picked = [s for rule in FIX_RULES for s in by_rule[rule]]
    rng = random.Random(seed)
    rng.shuffle(picked)
    return picked[:total]
