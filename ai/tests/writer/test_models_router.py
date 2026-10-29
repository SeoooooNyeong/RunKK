"""운영자가 부르는 모델 관리 API: GET /models, POST /models, POST /models/{id}/activate, POST /models/rollback."""

from __future__ import annotations

from collections.abc import AsyncIterator

import httpx
import pytest
from fastapi import FastAPI

from runkk_ai.writer import InMemoryModelRegistry, ModelsRouter


@pytest.fixture
def registry() -> InMemoryModelRegistry:
    return InMemoryModelRegistry()


@pytest.fixture
async def client(registry: InMemoryModelRegistry) -> AsyncIterator[httpx.AsyncClient]:
    app = FastAPI()
    app.include_router(ModelsRouter)
    app.state.model_registry = registry
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://ai") as c:
        yield c


async def test_list_is_empty_before_registration(client: httpx.AsyncClient) -> None:
    response = await client.get("/models")

    assert response.status_code == 200
    assert response.json() == {"models": [], "active": None}


async def test_register_returns_the_model_and_turns_it_on(client: httpx.AsyncClient) -> None:
    response = await client.post(
        "/models", json={"modelId": "v1-qwen3-4b", "baseUrl": "http://writer:8080", "note": "통과율 1.0"}
    )

    assert response.status_code == 201
    body = response.json()
    assert (body["modelId"], body["baseUrl"], body["note"], body["active"]) == (
        "v1-qwen3-4b",
        "http://writer:8080",
        "통과율 1.0",
        True,
    )
    assert (await client.get("/models")).json()["active"] == "v1-qwen3-4b"


async def test_activate_switches_the_model(client: httpx.AsyncClient) -> None:
    await client.post("/models", json={"modelId": "v1", "baseUrl": "http://writer:8080"})
    await client.post("/models", json={"modelId": "v2", "baseUrl": "http://writer-v2:8080"})

    response = await client.post("/models/v2/activate")

    assert response.status_code == 200
    assert response.json()["modelId"] == "v2"
    assert (await client.get("/models")).json()["active"] == "v2"


async def test_rollback_returns_to_the_previous_model(client: httpx.AsyncClient) -> None:
    await client.post("/models", json={"modelId": "v1", "baseUrl": "http://writer:8080"})
    await client.post("/models", json={"modelId": "v2", "baseUrl": "http://writer-v2:8080"})
    await client.post("/models/v2/activate")

    response = await client.post("/models/rollback")

    assert response.status_code == 200
    assert response.json()["modelId"] == "v1"


async def test_unknown_model_is_404(client: httpx.AsyncClient) -> None:
    response = await client.post("/models/none/activate")

    assert response.status_code == 404
    assert response.json() == {"reason": "MODEL_NOT_FOUND"}


async def test_rollback_without_a_previous_model_is_409(client: httpx.AsyncClient) -> None:
    await client.post("/models", json={"modelId": "v1", "baseUrl": "http://writer:8080"})

    response = await client.post("/models/rollback")

    assert response.status_code == 409
    assert response.json() == {"reason": "NO_PREVIOUS_MODEL"}


@pytest.mark.parametrize(
    "body",
    [
        {"baseUrl": "http://writer:8080"},
        {"modelId": "v1"},
        {"modelId": "", "baseUrl": "http://writer:8080"},
        {"modelId": "v1", "baseUrl": "writer:8080"},
        {"modelId": "v1", "baseUrl": "file:///models"},
    ],
)
async def test_bad_registration_is_422(client: httpx.AsyncClient, body: dict[str, str]) -> None:
    assert (await client.post("/models", json=body)).status_code == 422
