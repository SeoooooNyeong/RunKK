"""runkk_ai.main: lifespan 조립(D-60). 키가 없어도 뜨고, 실제 외부 호출 없이 대체 응답을 낸다."""

from __future__ import annotations

import httpx
import pytest

from runkk_ai import main as main_module
from runkk_ai.comment import ClaudeClient, CommentService, OpenAiClient
from runkk_ai.face import AvatarService, NodeAvatarClient
from runkk_ai.main import create_app
from tests.conftest import AUTH, JPEG_HEAD, VALID_SUMMARY, FakeAvatarClient, FakeLlmClient, make_settings


async def test_lifespan_builds_services_from_settings_values() -> None:
    app = create_app(make_settings(llm_daily_hard_cap=7, face_timeout_s=3.0, max_image_bytes=2048))

    async with app.router.lifespan_context(app):
        comment_service = app.state.comment_service
        face_service = app.state.face_service
        assert isinstance(comment_service, CommentService)
        assert isinstance(comment_service._llm, ClaudeClient)
        assert comment_service._daily_cap == 7
        assert isinstance(face_service, AvatarService)
        assert isinstance(face_service._client, NodeAvatarClient)
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
        avatar = app.state.face_service._client

    assert llm._client is None or llm._client.is_closed()
    assert avatar._client.is_closed


@pytest.mark.parametrize(
    ("provider", "model", "keys", "has_sdk"),
    [
        ("openai", "gpt-test", {"openai_api_key": "sk-openai"}, True),
        ("openai", "gpt-test", {"anthropic_api_key": "sk-anthropic"}, False),
        ("claude", "claude-haiku-5-5", {"anthropic_api_key": "sk-anthropic"}, True),
        ("claude", "claude-haiku-5-5", {"openai_api_key": "sk-openai"}, False),
    ],
    ids=["openai-own-key", "openai-other-key", "claude-own-key", "claude-other-key"],
)
async def test_provider_gets_only_its_own_key(provider: str, model: str, keys: dict[str, str], has_sdk: bool) -> None:
    """제공자 클래스와 키 항목은 한 표에서 함께 고른다(L10). 다른 제공자 키로 SDK를 만들지 않는다."""
    app = create_app(make_settings(llm_provider=provider, llm_model=model, **keys))

    async with app.router.lifespan_context(app):
        llm = app.state.comment_service._llm
        assert (llm._client is not None) is has_sdk


class _BrokenCloseLlm(FakeLlmClient):
    async def aclose(self) -> None:
        raise RuntimeError("close failed")


def _broken_build(_settings: object) -> FakeAvatarClient:
    raise RuntimeError("avatar build failed")


async def test_llm_is_closed_when_avatar_client_cannot_be_built(monkeypatch: pytest.MonkeyPatch) -> None:
    """두 번째 자원 생성이 실패해도 먼저 만든 LLM 클라이언트를 닫는다(L1)."""
    llm = FakeLlmClient()
    monkeypatch.setattr(main_module, "_build_llm_client", lambda _settings: llm)
    monkeypatch.setattr(main_module, "_build_avatar_client", _broken_build)
    app = create_app(make_settings())

    with pytest.raises(RuntimeError, match="avatar build failed"):
        async with app.router.lifespan_context(app):
            pass

    assert llm.closed


async def test_avatar_client_is_closed_even_if_llm_close_fails(monkeypatch: pytest.MonkeyPatch) -> None:
    """한 자원의 aclose()가 실패해도 다른 자원은 닫는다(L1)."""
    avatar = FakeAvatarClient()
    monkeypatch.setattr(main_module, "_build_llm_client", lambda _settings: _BrokenCloseLlm())
    monkeypatch.setattr(main_module, "_build_avatar_client", lambda _settings: avatar)
    app = create_app(make_settings())

    with pytest.raises(RuntimeError, match="close failed"):
        async with app.router.lifespan_context(app):
            pass

    assert avatar.closed


async def test_app_starts_without_provider_keys_and_falls_back() -> None:
    """제공자 키·아바타 서버 주소가 없어도 뜨고, 호출은 실패 응답(api가 규칙 문구·기본 캐릭터로 대체)."""
    app = create_app(make_settings(anthropic_api_key=None, face_api_url=None))

    async with (
        app.router.lifespan_context(app),
        httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://ai", headers=AUTH) as c,
    ):
        health = await c.get("/health")
        comment = await c.post("/comments", json=VALID_SUMMARY)
        face = await c.post(
            "/users/42/faces/jobs", content=JPEG_HEAD, headers={"Content-Type": "application/octet-stream"}
        )

    assert health.status_code == 200
    assert (comment.status_code, comment.json()) == (503, {"reason": "LLM_UNAVAILABLE"})
    assert (face.status_code, face.json()) == (503, {"reason": "UPSTREAM_FAILED"})


async def test_unhandled_exception_is_500_with_reason_body() -> None:
    """처리 안 된 예외도 text/plain이 아니라 {"reason"} JSON이다(N3)."""
    app = create_app(make_settings())

    @app.get("/boom")
    async def boom() -> None:
        raise RuntimeError("secret detail")

    transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
    async with httpx.AsyncClient(transport=transport, base_url="http://ai", headers=AUTH) as client:
        response = await client.get("/boom")

    assert response.status_code == 500
    assert response.json() == {"reason": "INTERNAL_ERROR"}


async def test_writer_is_off_without_url() -> None:
    """RUNKK_WRITER_URL이 writer 전체 스위치다. 비우면 등록부도 비어 있고 피드백은 템플릿 그대로."""
    app = create_app(make_settings())

    async with app.router.lifespan_context(app):
        assert app.state.runbody_service._polisher is None
        assert await app.state.model_registry.models() == []


async def test_writer_url_registers_and_activates_the_model() -> None:
    from runkk_ai.writer import InMemoryModelRegistry, WriterService

    app = create_app(make_settings(writer_url="http://writer:8080", writer_timeout_s=30.0))

    async with app.router.lifespan_context(app):
        writer = app.state.runbody_service._polisher.__self__
        registry = app.state.model_registry
        active = await registry.active()
        assert isinstance(writer, WriterService)
        assert isinstance(registry, InMemoryModelRegistry)
        assert writer._timeout_s == 30.0
        assert active is not None
        assert (active.model_id, active.base_url) == ("runkk-writer-v1-qwen3-4b", "http://writer:8080")


async def test_restart_keeps_the_model_an_operator_activated() -> None:
    """운영자가 /models로 v2를 켜 둔 뒤 다시 뜨면, 환경 변수의 v1이 아니라 v2가 그대로 켜져 있다."""
    from runkk_ai.writer import InMemoryModelRegistry

    registry = InMemoryModelRegistry()
    app = create_app(make_settings(writer_url="http://writer:8080"))
    app.state.model_registry = registry

    async with app.router.lifespan_context(app):
        await app.state.model_registry.register("v2", "http://writer-v2:8080")
        await app.state.model_registry.activate("v2")
        kept = app.state.model_registry

    app = create_app(make_settings(writer_url="http://writer:8080"))
    async with app.router.lifespan_context(app):
        app.state.model_registry = kept  # PostgreSQL 저장소처럼 등록부가 재시작을 넘어 남은 경우
        active = await kept.active()

    assert active is not None
    assert active.model_id == "v2"


async def test_withdrawal_covers_avatar_server_only_when_configured() -> None:
    """아바타 서버를 켠 환경에서만 탈퇴 파기에 아바타 폴더를 넣는다(-257). 끈 환경에서 넣으면 탈퇴가 늘 503이 된다."""
    on = create_app(make_settings(face_api_url="http://avatar:3000/api/v1"))
    off = create_app(make_settings(face_api_url=None))

    async with on.router.lifespan_context(on):
        assert set(on.state.withdrawal_service._purgers) == {"sessionResults", "coachMessages", "avatarImages"}
    async with off.router.lifespan_context(off):
        assert set(off.state.withdrawal_service._purgers) == {"sessionResults", "coachMessages"}


async def test_coach_retention_sweeper_runs_with_the_app() -> None:
    """코치 대화 90일 파기(-258)는 기동 때 한 번, 그 뒤 주기적으로 돈다. 종료 때 멈춘다."""
    app = create_app(make_settings())

    async with app.router.lifespan_context(app):
        sweeper = app.state.coach_retention
        assert sweeper.days == 90
        task = sweeper._task
        assert task is not None
        assert not task.done()

    assert task.done()
