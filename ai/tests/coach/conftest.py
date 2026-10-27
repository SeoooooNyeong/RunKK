"""코치 도구 시험용 fixture: 실측 2회(10/18, 10/19)를 저장한 메모리 저장소와 고정 시계."""

from __future__ import annotations

from datetime import datetime

import pytest

from runkk_ai.coach import CoachTools
from runkk_ai.runbody import (
    InMemoryResultRepository,
    MonthlyService,
    SessionIn,
    SessionRecord,
    SessionResultOut,
    analyze_session,
)
from tests.runbody.conftest import kst, load_body

NOW = kst("2026-10-20 09:00")


@pytest.fixture(scope="session")
def results() -> list[SessionResultOut]:
    first = analyze_session(SessionIn.model_validate(load_body("first_run")), [])
    second = analyze_session(SessionIn.model_validate(load_body("second_run")), [first])
    return [first, second]


async def save(repo: InMemoryResultRepository, result: SessionResultOut, user_id: str | None = None) -> None:
    uid = user_id or result.userId
    started = result.summary.startedAt
    await repo.save(SessionRecord(result.sessionId, uid, started, "done", started, result=result))


@pytest.fixture
async def repo(results: list[SessionResultOut]) -> InMemoryResultRepository:
    repository = InMemoryResultRepository()
    for r in results:
        await save(repository, r)
    return repository


def clock() -> datetime:
    return NOW


@pytest.fixture
def tools(repo: InMemoryResultRepository) -> CoachTools:
    return CoachTools(repo, MonthlyService(repo, clock=clock), clock=clock)


# ---------- 대화 시험용 가짜 모델 ----------
from collections.abc import AsyncIterator, Sequence  # noqa: E402
from dataclasses import dataclass, field  # noqa: E402
from typing import Any  # noqa: E402

from runkk_ai.coach import CoachService, InMemoryCoachStore, ModelTurn, TextDelta, ToolCall  # noqa: E402
from runkk_ai.coach.model import ModelEvent  # noqa: E402


@dataclass
class Script:
    """한 turn: 흘려보낼 글자 조각과 끝에 고를 도구."""

    texts: list[str] = field(default_factory=list)
    calls: list[ToolCall] = field(default_factory=list)


@dataclass
class FakeCoachModel:
    scripts: list[Script]
    error: BaseException | None = None
    seen: list[dict[str, Any]] = field(default_factory=list)

    @property
    def name(self) -> str:
        return "fake-haiku"

    async def stream(
        self, system: str, messages: Sequence[dict[str, Any]], tools: Sequence[dict[str, Any]], *, allow_tools: bool
    ) -> AsyncIterator[ModelEvent]:
        self.seen.append({"system": system, "messages": list(messages), "allow_tools": allow_tools})
        if self.error is not None:
            raise self.error
        script = self.scripts.pop(0) if self.scripts else Script(texts=["끝"])
        for text in script.texts:
            yield TextDelta(text)
        calls = script.calls if allow_tools else []
        yield ModelTurn(text="".join(script.texts), tool_calls=calls, input_tokens=100, output_tokens=20)

    async def aclose(self) -> None:
        return None


def make_service(
    tools: CoachTools,
    model: FakeCoachModel | None,
    store: InMemoryCoachStore | None = None,
    daily_cap: int = 100,
    user_daily_cap: int = 10,
) -> CoachService:
    return CoachService(
        model,
        tools,
        store or InMemoryCoachStore(),
        daily_cap=daily_cap,
        user_daily_cap=user_daily_cap,
        timeout_s=5,
        clock=clock,
    )
