"""runkk_ai.main: lifespan 조립(D-60). 키가 없어도 뜨고, 실제 외부 호출 없이 대체 응답을 낸다."""

from __future__ import annotations

import httpx
import pytest

from runkk_ai.comment import ClaudeClient, CommentService, OpenAiClient
from runkk_ai.face import ExternalFaceApi, FaceTransformService
from runkk_ai.main import create_app
from tests.conftest import AUTH, JPEG_HEAD, VALID_SUMMARY, make_settings


async def test_lifespan_builds_services_from_settings_values() -> None:
    app = create_app(make_settings(llm_daily_hard_cap=7, face_timeout_s=3.0, max_image_bytes=2048))

    async with app.router.lifespan_context(app):
        comment_service = app.state.comment_service
        face_service = app.state.face_service
        assert isinstance(comment_service, CommentService)
        assert isinstance(comment_service._llm, ClaudeClient)
        assert comment_service._daily_cap == 7
        assert isinstance(face_service, FaceTransformService)
        assert isinstance(face_service._transformer, ExternalFaceApi)
        assert face_service.max_image_bytes == 2048
        assert face_service._timeout_s == 3.0


async def test_lifespan_picks_openai_when_configured() -> None:
    app = create_app(make_settings(llm_provider="openai", llm_model="gpt-test"))

    async with app.router.lifespan_context(app):
        llm = app.state.comment_service._llm
        assert isinstance(llm, OpenAiClient)
        assert llm.model == "gpt-test"


async def test_lifespan_closes_clients_on_shutdown() -> None:
    app = create_app(make_settings())

    async with app.router.lifespan_context(app):
        llm = app.state.comment_service._llm
        transformer = app.state.face_service._transformer

    assert llm._client is None or llm._client.is_closed()
    assert transformer._client.is_closed


async def test_local_face_model_is_refused_until_selected() -> None:
    app = create_app(make_settings(face_impl="local"))

    with pytest.raises(RuntimeError, match="C-011"):
        async with app.router.lifespan_context(app):
            pass


async def test_app_starts_without_provider_keys_and_falls_back() -> None:
    """제공자 키·얼굴 API 주소가 없어도 뜨고, 호출은 실패 응답(api가 규칙 문구·기본 캐릭터로 대체)."""
    app = create_app(make_settings(anthropic_api_key=None, face_api_url=None))

    async with (
        app.router.lifespan_context(app),
        httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://ai", headers=AUTH) as c,
    ):
        health = await c.get("/health")
        comment = await c.post("/comments", json=VALID_SUMMARY)
        face = await c.post("/faces", content=JPEG_HEAD, headers={"Content-Type": "application/octet-stream"})

    assert health.status_code == 200
    assert (comment.status_code, comment.json()) == (503, {"reason": "LLM_UNAVAILABLE"})
    assert (face.status_code, face.json()) == (503, {"reason": "UPSTREAM_FAILED"})
