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
