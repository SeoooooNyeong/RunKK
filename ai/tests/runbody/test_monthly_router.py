"""GET /users/{userId}/months/{yearMonth} 시험. 1회 분석과 같은 저장소를 읽는다."""

from __future__ import annotations

import json
from datetime import UTC, datetime
from typing import Any

import httpx

from runkk_ai.runbody import InMemoryResultRepository, MonthlyService, RunbodyService

MONTH = "/users/u_demo/months/2026-10"


async def _analyze(client: httpx.AsyncClient, service: RunbodyService, body: dict[str, Any]) -> None:
    await client.post(
        f"/sessions/{body['sessionId']}/analyze",
        content=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
    )
    await service.wait(body["sessionId"])


async def test_month_after_two_runs(
    client: httpx.AsyncClient,
    runbody_service: RunbodyService,
    first_run_body: dict[str, Any],
    second_run_body: dict[str, Any],
) -> None:
    await _analyze(client, runbody_service, first_run_body)
    await _analyze(client, runbody_service, second_run_body)

    response = await client.get(MONTH)

    assert response.status_code == 200
    body = response.json()
    assert (body["userId"], body["yearMonth"], body["sessionCount"], body["distanceMeters"]) == (
        "u_demo",
        "2026-10",
        2,
        5035,
    )
    assert body["axes"]["intensity"] == {"average": 75, "count": 2, "ref": 55}
    assert body["bests"]["longestRunMeters"]["value"] == 3008
    assert body["load"]["availableFrom"] == "2026-11-15"
    assert body["asOf"] == "2026-10-19T10:30:00Z"  # 시험 시계(FIXED_NOW)


async def test_month_without_runs_is_empty(client: httpx.AsyncClient) -> None:
    response = await client.get("/users/u_nobody/months/2026-10")

    assert response.status_code == 200
    assert (response.json()["sessionCount"], response.json()["paceSecondsPerKm"]) == (0, None)


async def test_invalid_year_month_is_422(client: httpx.AsyncClient) -> None:
    for bad in ("2026-13", "2026-1", "26-10", "2026-10-01"):
        response = await client.get(f"/users/u_demo/months/{bad}")

        assert response.status_code == 422, bad
        assert response.json() == {"reason": "INVALID_REQUEST"}


async def test_month_requires_internal_token(anon_client: httpx.AsyncClient) -> None:
    response = await anon_client.get(MONTH)

    assert response.status_code == 401


async def test_past_month_is_cut_at_month_end(runbody_repository: InMemoryResultRepository) -> None:
    service = MonthlyService(runbody_repository, clock=lambda: datetime(2026, 12, 5, tzinfo=UTC))

    month = await service.month("u_demo", "2026-10")

    assert month.asOf.isoformat() == "2026-10-31T23:59:59.999999+09:00"
    assert month.sessionCount == 0


async def test_default_clock_is_now(runbody_repository: InMemoryResultRepository) -> None:
    month = await MonthlyService(runbody_repository).month("u_demo", "2026-10")

    assert month.asOf <= datetime.now(UTC)
