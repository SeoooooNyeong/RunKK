"""runkk_ai.core: Settings, verify_internal_token, HealthRouter와 create_app의 라우터 등록."""

from __future__ import annotations

import httpx
import pytest
from pydantic import ValidationError

from runkk_ai.core import Settings
from runkk_ai.main import create_app
from tests.conftest import AUTH, TOKEN, VALID_SUMMARY, make_settings


async def test_health_answers_without_token(anon_client: httpx.AsyncClient) -> None:
    response = await anon_client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


@pytest.mark.parametrize(
    ("method", "path", "kwargs"),
    [
        ("POST", "/comments", {"json": VALID_SUMMARY}),
        ("POST", "/faces", {"content": b"\xff\xd8\xff", "headers": {"Content-Type": "application/octet-stream"}}),
    ],
    ids=["comments", "faces"],
)
async def test_missing_internal_token_is_401(
    anon_client: httpx.AsyncClient, method: str, path: str, kwargs: dict[str, object]
) -> None:
    response = await anon_client.request(method, path, **kwargs)  # type: ignore[arg-type]

    assert response.status_code == 401


@pytest.mark.parametrize("token", ["wrong-token", "", TOKEN + "x", TOKEN[:-1]])
async def test_wrong_internal_token_is_401(anon_client: httpx.AsyncClient, token: str) -> None:
    response = await anon_client.post("/comments", json=VALID_SUMMARY, headers={"X-Internal-Token": token})

    assert response.status_code == 401


async def test_token_check_runs_before_body_validation(anon_client: httpx.AsyncClient) -> None:
    """토큰이 없으면 본문이 잘못되어도 422가 아니라 401이다(본문 내용을 알려 주지 않는다)."""
    response = await anon_client.post("/comments", json={"memberId": 7})

    assert response.status_code == 401


async def test_docs_are_hidden_by_default(client: httpx.AsyncClient) -> None:
    for path in ("/docs", "/redoc", "/openapi.json"):
        response = await client.get(path)
        assert response.status_code == 404, path


async def test_docs_can_be_exposed_for_local_development() -> None:
    app = create_app(make_settings(expose_docs=True))
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://ai") as c:
        response = await c.get("/openapi.json")

    assert response.status_code == 200
    assert {"/comments", "/faces", "/health"} <= set(response.json()["paths"])


def test_settings_defaults_follow_design() -> None:
    settings = make_settings()

    assert settings.llm_provider == "claude"
    assert settings.llm_timeout_s == 2.5
    assert settings.llm_max_tokens == 200
    assert settings.llm_daily_hard_cap == 240
    assert settings.max_image_bytes == 1024 * 1024
    assert settings.face_impl == "external"
    assert settings.face_timeout_s < 15.0  # api AiServerClient 읽기 제한(face 15초)보다 짧아야 한다
    assert settings.expose_docs is False
    assert settings.internal_token.get_secret_value() == TOKEN


def test_settings_read_only_from_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("RUNKK_INTERNAL_TOKEN", "from-environment-0123456789")
    monkeypatch.setenv("RUNKK_LLM_PROVIDER", "openai")
    monkeypatch.setenv("RUNKK_ANTHROPIC_API_KEY", "sk-test")

    settings = Settings(_env_file=None)  # type: ignore[call-arg]

    assert settings.internal_token.get_secret_value() == "from-environment-0123456789"
    assert settings.llm_provider == "openai"
    assert settings.anthropic_api_key is not None
    assert "sk-test" not in repr(settings)  # 비밀값은 repr·로그에 드러나지 않는다


def test_settings_require_internal_token(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("RUNKK_INTERNAL_TOKEN", raising=False)

    with pytest.raises(ValidationError):
        Settings(_env_file=None)  # type: ignore[call-arg]


def test_settings_reject_short_internal_token() -> None:
    with pytest.raises(ValidationError):
        make_settings(internal_token="short")


def test_settings_reject_unknown_provider() -> None:
    with pytest.raises(ValidationError):
        make_settings(llm_provider="gemini")


async def test_only_designed_paths_exist(client: httpx.AsyncClient) -> None:
    response = await client.get("/admin", headers=AUTH)

    assert response.status_code == 404


def _openapi_operations() -> dict[str, dict[str, object]]:
    app = create_app(make_settings(expose_docs=True))
    paths: dict[str, dict[str, dict[str, object]]] = app.openapi()["paths"]
    return {path: op for path, item in paths.items() for op in item.values()}


def test_every_route_except_health_checks_internal_token() -> None:
    """보안 회귀: 새 라우터를 토큰 검사 없이 붙이면 실패한다(13.3 보안, HealthRouter만 예외)."""
    operations = _openapi_operations()

    assert set(operations) == {"/health", "/comments", "/faces"}
    for path, operation in operations.items():
        params = operation.get("parameters", [])
        guarded = any(p["in"] == "header" and p["name"].lower() == "x-internal-token" for p in params)  # type: ignore[attr-defined, index]
        assert guarded is (path != "/health"), path


def test_faces_does_not_take_multipart_upload() -> None:
    """셀카를 UploadFile(1MB 넘으면 임시 파일)로 받지 않는다(D-59). 본문은 라우터가 직접 읽는다."""
    operations = _openapi_operations()

    assert "requestBody" not in operations["/faces"]
