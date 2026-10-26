"""RUN바디 시험용 fixture.

tests/fixtures/*.json은 갤럭시 워치 실측 러닝 2회(10/18, 10/19)를 요청 형식으로 바꾼 것이다.
"""

from __future__ import annotations

import asyncio
import json
from collections.abc import AsyncIterator, Sequence
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import pytest

from runkk_ai.runbody import InMemoryResultRepository, RunbodyService, SessionIn, SessionResultOut, analyze_session

FIXTURES = Path(__file__).resolve().parents[1] / "fixtures"


def load_body(name: str) -> dict[str, Any]:
    body: dict[str, Any] = json.loads((FIXTURES / f"{name}.json").read_text(encoding="utf-8"))
    return body


def derive(
    session: SessionIn,
    *,
    drop_heart_rate: bool = False,
    drop_gps: bool = False,
    drop_cadence: bool = False,
    max_seconds: int | None = None,
) -> SessionIn:
    """실측 세션에서 센서 하나를 빼거나 앞부분만 남긴 세션을 만든다(분기 시험용)."""
    body = session.model_dump(mode="json")
    samples: list[dict[str, Any]] = body["samples"]
    if drop_heart_rate:
        samples = [{**s, "heartRate": None} for s in samples]
        samples = [s for s in samples if any(s[k] is not None for k in ("speed", "cadence", "distance"))]
        body["recoveryHeartRates"] = []
    if drop_cadence:
        samples = [{**s, "cadence": None} for s in samples]
    if max_seconds is not None:
        limit_ms = max_seconds * 1000
        samples = [s for s in samples if s["tMs"] < limit_ms]
        body["gps"] = [g for g in body["gps"] if g["tMs"] < limit_ms]
        body["activeMs"] = limit_ms
        body["distanceMeters"] = sum(s["distance"] or 0 for s in samples)
    if drop_gps:
        body["gps"] = []
    body["samples"] = samples
    return SessionIn.model_validate(body)


@pytest.fixture(scope="session")
def first_run_body() -> dict[str, Any]:
    return load_body("first_run")


@pytest.fixture(scope="session")
def second_run_body() -> dict[str, Any]:
    return load_body("second_run")


@pytest.fixture(scope="session")
def first_run(first_run_body: dict[str, Any]) -> SessionIn:
    return SessionIn.model_validate(first_run_body)


@pytest.fixture(scope="session")
def second_run(second_run_body: dict[str, Any]) -> SessionIn:
    return SessionIn.model_validate(second_run_body)


@dataclass
class FakeAnalyzer:
    """엔진을 그대로 부르되 멈추거나(gate) 실패시킬(error) 수 있는 분석기. 부른 세션 ID를 남긴다."""

    gate: asyncio.Event | None = None
    error: BaseException | None = None
    calls: list[str] = field(default_factory=list)

    async def __call__(self, session: SessionIn, history: Sequence[SessionResultOut]) -> SessionResultOut:
        self.calls.append(session.sessionId)
        if self.gate is not None:
            await self.gate.wait()
        if self.error is not None:
            raise self.error
        return analyze_session(session, history)


@pytest.fixture
def fake_analyzer() -> FakeAnalyzer:
    return FakeAnalyzer()


@pytest.fixture
async def runbody_service(
    fake_analyzer: FakeAnalyzer, runbody_repository: InMemoryResultRepository
) -> AsyncIterator[RunbodyService]:
    """tests/runbody 안에서는 가짜 분석기를 끼운 서비스로 루트 conftest의 runbody_service를 덮는다."""
    service = RunbodyService(runbody_repository, analyzer=fake_analyzer)
    yield service
    if fake_analyzer.gate is not None:
        fake_analyzer.gate.set()
    await service.aclose()


KST = timezone(timedelta(hours=9))


def kst(text: str) -> datetime:
    """'2026-10-19 19:09' → 한국 시간 datetime."""
    return datetime.strptime(text, "%Y-%m-%d %H:%M").replace(tzinfo=KST)


def retime(
    base: SessionResultOut, session_id: str, started_at: datetime, distance_m: int | None = None
) -> SessionResultOut:
    """실측 결과를 복사해 세션 ID·시작 시각·거리만 바꾼다(월간·부하 시험용 가상 기록)."""
    summary = base.summary.model_copy(
        update={"startedAt": started_at, "distanceMeters": distance_m or base.summary.distanceMeters}
    )
    return base.model_copy(update={"sessionId": session_id, "summary": summary})


@pytest.fixture(scope="session")
def first_result(first_run: SessionIn) -> SessionResultOut:
    return analyze_session(first_run, [])


@pytest.fixture(scope="session")
def second_result(first_run: SessionIn, second_run: SessionIn, first_result: SessionResultOut) -> SessionResultOut:
    return analyze_session(second_run, [first_result])
