"""runkk_ai.core: Settings, InternalTokenMiddleware, HealthRouter와 create_app의 조립."""

from __future__ import annotations

import httpx
import pytest
from fastapi import FastAPI
from pydantic import SecretStr, ValidationError
from starlette.types import Receive, Scope, Send

from runkk_ai.core import InternalTokenMiddleware, Settings
from runkk_ai.main import create_app
from tests.conftest import AUTH, TOKEN, VALID_SUMMARY, make_settings

UNAUTHORIZED = {"reason": "UNAUTHORIZED"}


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
    assert response.json() == UNAUTHORIZED  # 다른 실패와 같은 {"reason"} 형식(L13)


@pytest.mark.parametrize("token", ["wrong-token", "", TOKEN + "x", TOKEN[:-1]])
async def test_wrong_internal_token_is_401(anon_client: httpx.AsyncClient, token: str) -> None:
    response = await anon_client.post("/comments", json=VALID_SUMMARY, headers={"X-Internal-Token": token})

    assert response.status_code == 401
    assert response.json() == UNAUTHORIZED


async def test_token_check_runs_before_body_validation(anon_client: httpx.AsyncClient) -> None:
    """토큰이 없으면 본문이 잘못되어도 422가 아니라 401이다(본문 내용을 알려 주지 않는다)."""
    response = await anon_client.post("/comments", json={"memberId": 7})

    assert response.status_code == 401


async def test_token_check_runs_before_json_parsing(anon_client: httpx.AsyncClient) -> None:
    """깨진 JSON도 토큰 검사가 먼저다(L15). FastAPI 의존성은 본문 파싱 뒤에 돌아 미들웨어가 막는다."""
    response = await anon_client.post("/comments", content=b"{not json", headers={"Content-Type": "application/json"})

    assert response.status_code == 401
    assert response.json() == UNAUTHORIZED


async def test_face_body_is_not_read_without_token(anon_client: httpx.AsyncClient) -> None:
    """토큰이 없으면 큰 본문이어도 413이 아니라 401이다(본문을 읽기 전에 끊는다)."""
    response = await anon_client.post(
        "/faces",
        content=b"\xff\xd8\xff" + b"\x00" * (2 * 1024 * 1024),
        headers={"Content-Type": "application/octet-stream"},
    )

    assert response.status_code == 401


def _protected_operations() -> list[tuple[str, str]]:
    """등록된 모든 연산(OpenAPI 기준, 포함 라우터까지)에서 /health만 뺀다."""
    paths: dict[str, dict[str, object]] = create_app(make_settings(expose_docs=True)).openapi()["paths"]
    return sorted((method.upper(), path) for path, item in paths.items() if path != "/health" for method in item)


def test_protected_operations_are_the_designed_ones() -> None:
    assert _protected_operations() == [
        ("GET", "/models"),
        ("GET", "/sessions/{session_id}"),
        ("GET", "/sessions/{session_id}/status"),
        ("GET", "/users/{user_id}/months/{year_month}"),
        ("POST", "/coach/chat"),
        ("POST", "/comments"),
        ("POST", "/faces"),
        ("POST", "/models"),
        ("POST", "/models/rollback"),
        ("POST", "/models/{model_id}/activate"),
        ("POST", "/sessions/{session_id}/analyze"),
    ]


@pytest.mark.parametrize(("method", "path"), _protected_operations())
async def test_every_route_except_health_checks_internal_token(
    anon_client: httpx.AsyncClient, method: str, path: str
) -> None:
    """보안 회귀: 새 라우터를 붙여도 /health 말고는 토큰 없이 닿지 않는다(13.3 보안)."""
    response = await anon_client.request(method, path)

    assert response.status_code == 401


async def test_unknown_path_without_token_is_401(anon_client: httpx.AsyncClient) -> None:
    response = await anon_client.get("/admin")

    assert response.status_code == 401


async def test_only_designed_paths_exist(client: httpx.AsyncClient) -> None:
    response = await client.get("/admin", headers=AUTH)

    assert response.status_code == 404
    assert response.json() == {"reason": "NOT_FOUND"}


async def test_wrong_method_body_has_reason(client: httpx.AsyncClient) -> None:
    response = await client.get("/comments")

    assert response.status_code == 405
    assert response.json() == {"reason": "METHOD_NOT_ALLOWED"}
    assert response.headers["allow"] == "POST"


async def test_docs_are_hidden_by_default(client: httpx.AsyncClient) -> None:
    for path in ("/docs", "/redoc", "/openapi.json"):
        response = await client.get(path)
        assert response.status_code == 404, path


async def test_docs_can_be_exposed_for_local_development() -> None:
    app = create_app(make_settings(expose_docs=True))
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://ai") as c:
        response = await c.get("/openapi.json")
        docs = await c.get("/docs")

    assert response.status_code == 200
    assert docs.status_code == 200
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
    monkeypatch.setenv("RUNKK_LLM_MODEL", "gpt-test")
    monkeypatch.setenv("RUNKK_ANTHROPIC_API_KEY", "sk-test")

    settings = Settings(_env_file=None)  # type: ignore[call-arg]

    assert settings.internal_token.get_secret_value() == "from-environment-0123456789"
    assert settings.llm_provider == "openai"
    assert settings.anthropic_api_key is not None
    assert "sk-test" not in repr(settings)  # 비밀값은 repr·로그에 드러나지 않는다


def test_empty_environment_values_fall_back_to_defaults(monkeypatch: pytest.MonkeyPatch) -> None:
    """compose가 비어 있는 변수를 ""로 넘겨도(M1 environment 목록) 기본값으로 뜬다."""
    monkeypatch.setenv("RUNKK_INTERNAL_TOKEN", "from-environment-0123456789")
    for name in ("LLM_TIMEOUT_S", "LLM_DAILY_HARD_CAP", "ANTHROPIC_API_KEY", "FACE_API_URL", "EXPOSE_DOCS"):
        monkeypatch.setenv(f"RUNKK_{name}", "")

    settings = Settings(_env_file=None)  # type: ignore[call-arg]

    assert settings.llm_timeout_s == 2.5
    assert settings.llm_daily_hard_cap == 240
    assert settings.anthropic_api_key is None
    assert settings.face_api_url is None
    assert settings.expose_docs is False


def test_settings_require_internal_token(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("RUNKK_INTERNAL_TOKEN", raising=False)

    with pytest.raises(ValidationError):
        Settings(_env_file=None)  # type: ignore[call-arg]


def test_settings_reject_short_internal_token() -> None:
    with pytest.raises(ValidationError):
        make_settings(internal_token="short")


@pytest.mark.parametrize(
    "token",
    ["change-me-to-a-long-random-string", "CHANGE-ME-0123456789abcdef", "prefix-change-me-0123456789"],
)
def test_settings_reject_placeholder_internal_token(token: str) -> None:
    """.env.example 자리표시 값을 그대로 두면 기동하지 않는다(L12)."""
    with pytest.raises(ValidationError, match="RUNKK_INTERNAL_TOKEN"):
        make_settings(internal_token=token)


@pytest.mark.parametrize("token", ["s3cr3tQ", "change-me-to-a-long-random-string"])
def test_settings_error_does_not_echo_secret(token: str) -> None:
    """설정 오류 문구가 컨테이너 로그로 나가므로 입력값(토큰)을 싣지 않는다."""
    with pytest.raises(ValidationError) as exc_info:
        make_settings(internal_token=token)

    assert token not in str(exc_info.value)


def test_settings_reject_unknown_provider() -> None:
    with pytest.raises(ValidationError):
        make_settings(llm_provider="gemini")


@pytest.mark.parametrize(
    ("provider", "model"),
    [("openai", "claude-haiku-5-5"), ("claude", "gpt-5-mini")],
    ids=["openai-with-claude-model", "claude-with-other-model"],
)
def test_settings_reject_model_of_other_provider(provider: str, model: str) -> None:
    """제공자만 바꾸고 모델을 그대로 두면 모든 호출이 조용히 503이 되므로 기동 때 막는다(L11)."""
    with pytest.raises(ValidationError, match="llm_model"):
        make_settings(llm_provider=provider, llm_model=model)


@pytest.mark.parametrize("timeout_s", [2.7, 3.0])
def test_create_app_rejects_llm_timeout_not_below_route_timeout(timeout_s: float) -> None:
    """LLM 제한은 라우터 전체 제한(comment.router.COMMENT_TIMEOUT_S)보다 짧아야 한다(H-22, L4)."""
    with pytest.raises(ValueError, match="COMMENT_TIMEOUT_S"):
        create_app(make_settings(llm_timeout_s=timeout_s))


def test_create_app_accepts_llm_timeout_below_route_timeout() -> None:
    create_app(make_settings(llm_timeout_s=2.69))


def test_faces_does_not_take_multipart_upload() -> None:
    """셀카를 UploadFile(1MB 넘으면 임시 파일)로 받지 않는다(D-59). 본문은 라우터가 직접 읽는다."""
    app = create_app(make_settings(expose_docs=True))
    operation = app.openapi()["paths"]["/faces"]["post"]

    assert "requestBody" not in operation


async def test_duplicate_internal_token_header_is_401(anon_client: httpx.AsyncClient) -> None:
    """같은 헤더가 둘이면 맞는 값이 섞여 있어도 거부한다(N2)."""
    headers = [("X-Internal-Token", TOKEN), ("X-Internal-Token", TOKEN)]

    response = await anon_client.post("/comments", json=VALID_SUMMARY, headers=headers)

    assert response.status_code == 401
    assert response.json() == UNAUTHORIZED


async def test_websocket_without_token_is_closed_with_policy_violation(app: FastAPI) -> None:
    """websocket scope도 토큰 없이는 안쪽 앱에 닿지 않는다(N1)."""
    inner_reached = False
    sent: list[dict[str, object]] = []

    async def inner(scope: Scope, receive: Receive, send: Send) -> None:
        nonlocal inner_reached
        inner_reached = True

    async def receive() -> dict[str, object]:
        return {"type": "websocket.connect"}

    async def send(message: dict[str, object]) -> None:
        sent.append(message)

    guard = InternalTokenMiddleware(inner, token=SecretStr(TOKEN), public_paths=("/health",))

    await guard({"type": "websocket", "path": "/ws", "headers": []}, receive, send)  # type: ignore[arg-type]

    assert not inner_reached
    assert sent == [{"type": "websocket.close", "code": 1008}]


async def test_lifespan_scope_passes_through() -> None:
    reached: list[str] = []

    async def inner(scope: Scope, receive: Receive, send: Send) -> None:
        reached.append(scope["type"])

    guard = InternalTokenMiddleware(inner, token=SecretStr(TOKEN), public_paths=())

    await guard({"type": "lifespan"}, None, None)  # type: ignore[arg-type]

    assert reached == ["lifespan"]
