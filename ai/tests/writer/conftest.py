"""writer 시험용 fixture. 실측 2회 결과로 brief를 만든다."""

from __future__ import annotations

from typing import Any

import pytest

from runkk_ai.runbody import SessionIn, SessionResultOut, analyze_session
from runkk_ai.writer.brief import writer_brief
from tests.runbody.conftest import load_body


@pytest.fixture(scope="session")
def bases() -> list[dict[str, Any]]:
    return [load_body("first_run"), load_body("second_run")]


@pytest.fixture(scope="session")
def second(bases: list[dict[str, Any]]) -> SessionResultOut:
    first = analyze_session(SessionIn.model_validate(bases[0]), [])
    return analyze_session(SessionIn.model_validate(bases[1]), [first])


@pytest.fixture(scope="session")
def brief(second: SessionResultOut) -> dict[str, Any]:
    return writer_brief(second)
