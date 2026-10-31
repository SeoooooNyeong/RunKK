"""탈퇴 회원 파기(S15P31A207-219): DELETE /users/{userId}가 분석 결과·코치 대화를 지우고 지운 수를 돌려준다."""

from __future__ import annotations

import logging
from datetime import UTC, datetime

import httpx
import pytest
from fastapi import FastAPI

from runkk_ai.coach import CoachMessage, InMemoryCoachStore
from runkk_ai.privacy import WithdrawalService, get_withdrawal_service
from runkk_ai.runbody import InMemoryResultRepository, SessionRecord

T0 = datetime(2026, 10, 29, 7, 0, tzinfo=UTC)


class _Broken:
    async def purge_user(self, user_id: str) -> int:
        raise RuntimeError("db down for " + user_id)


class _Slow:
    async def purge_user(self, user_id: str) -> int:
        import asyncio

        await asyncio.sleep(1)
        return 0


async def _seed(repository: InMemoryResultRepository, store: InMemoryCoachStore) -> None:
    for i, user in enumerate(["u1", "u1", "u2"]):
        await repository.claim(SessionRecord(f"s{i}", user, T0, "pending", T0))
    await store.append(CoachMessage("c1", "u1", "user", "안녕", T0))
    await store.append(CoachMessage("c1", "u1", "assistant", "네", T0))
    await store.append(CoachMessage("c2", "u2", "user", "안녕", T0))


@pytest.fixture
def stores() -> tuple[InMemoryResultRepository, InMemoryCoachStore]:
    return InMemoryResultRepository(), InMemoryCoachStore()


@pytest.fixture
def withdrawal(stores: tuple[InMemoryResultRepository, InMemoryCoachStore]) -> WithdrawalService:
    repository, store = stores
    return WithdrawalService({"sessionResults": repository, "coachMessages": store}, timeout_s=1.0)


@pytest.fixture
def wired(app: FastAPI, withdrawal: WithdrawalService) -> FastAPI:
    app.dependency_overrides[get_withdrawal_service] = lambda: withdrawal
    return app


async def test_delete_purges_results_and_conversations_and_is_idempotent(
    client: httpx.AsyncClient,
    wired: FastAPI,
    stores: tuple[InMemoryResultRepository, InMemoryCoachStore],
    caplog: pytest.LogCaptureFixture,
) -> None:
    caplog.set_level(logging.INFO)
    repository, store = stores
    await _seed(repository, store)

    first = await client.delete("/users/u1")
    again = await client.delete("/users/u1")

    assert (first.status_code, first.json()) == (200, {"sessionResults": 2, "coachMessages": 2})
    assert (again.status_code, again.json()) == (200, {"sessionResults": 0, "coachMessages": 0})
    assert await repository.get("s2") is not None  # 다른 사용자는 그대로
    assert await store.owner("c2") == "u2"
    ours = "\n".join(r.getMessage() for r in caplog.records if r.name.startswith("runkk_ai"))
    assert "user_purge result=ok detail= sessionResults=2 coachMessages=2" in ours
    assert "u1" not in ours  # 회원 식별자는 로그에 남기지 않는다


async def test_unknown_user_is_200_with_zeros(client: httpx.AsyncClient, wired: FastAPI) -> None:
    response = await client.delete("/users/nobody")

    assert (response.status_code, response.json()) == (200, {"sessionResults": 0, "coachMessages": 0})


@pytest.mark.parametrize("path", ["/users/a:b", "/users/" + "x" * 129])
async def test_bad_user_id_is_422(client: httpx.AsyncClient, wired: FastAPI, path: str) -> None:
    response = await client.delete(path)

    assert (response.status_code, response.json()) == (422, {"reason": "INVALID_REQUEST"})


async def test_store_failure_is_503_and_nothing_is_half_reported(
    client: httpx.AsyncClient, app: FastAPI, caplog: pytest.LogCaptureFixture
) -> None:
    """한 저장소라도 실패하면 503 STORAGE_UNAVAILABLE. api는 재시도하거나 파기 배치에서 다시 부른다."""
    caplog.set_level(logging.INFO)
    service = WithdrawalService(
        {"sessionResults": InMemoryResultRepository(), "coachMessages": _Broken()}, timeout_s=1.0
    )
    app.dependency_overrides[get_withdrawal_service] = lambda: service

    response = await client.delete("/users/u1")

    assert (response.status_code, response.json()) == (503, {"reason": "STORAGE_UNAVAILABLE"})
    assert "user_purge result=STORAGE_UNAVAILABLE" in caplog.text
    assert "RuntimeError" in caplog.text
    assert "db down" not in caplog.text  # 예외 문구는 남기지 않는다


async def test_slow_store_is_504(client: httpx.AsyncClient, app: FastAPI) -> None:
    service = WithdrawalService({"sessionResults": _Slow()}, timeout_s=0.05)
    app.dependency_overrides[get_withdrawal_service] = lambda: service

    response = await client.delete("/users/u1")

    assert (response.status_code, response.json()) == (504, {"reason": "STORAGE_TIMEOUT"})


async def test_requires_internal_token(anon_client: httpx.AsyncClient, wired: FastAPI) -> None:
    response = await anon_client.delete("/users/u1")

    assert (response.status_code, response.json()) == (401, {"reason": "UNAUTHORIZED"})
