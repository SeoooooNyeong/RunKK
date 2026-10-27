"""ai 서버 조립(6.1.16, D-60). main → core·comment·face·runbody 한 방향이고
어느 기능 모듈도 이 모듈을 import하지 않는다.

실행: uvicorn --factory runkk_ai.main:create_app
"""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator, Callable, Mapping
from contextlib import AsyncExitStack, asynccontextmanager
from http import HTTPStatus
from typing import Protocol, cast

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import SecretStr
from starlette.exceptions import HTTPException as StarletteHTTPException

from runkk_ai.comment import COMMENT_TIMEOUT_S, ClaudeClient, CommentRouter, CommentService, LlmClient, OpenAiClient
from runkk_ai.core import HealthRouter, InternalTokenMiddleware, Settings
from runkk_ai.face import ExternalFaceApi, FaceRouter, FaceTransformer, FaceTransformService
from runkk_ai.runbody import InMemoryResultRepository, MonthlyRouter, MonthlyService, RunbodyRouter, RunbodyService

logger = logging.getLogger(__name__)

_DOCS_URL = "/docs"
_DOCS_OAUTH2_REDIRECT_URL = "/docs/oauth2-redirect"
_REDOC_URL = "/redoc"
_OPENAPI_URL = "/openapi.json"
# 토큰 없이 닿는 경로. compose healthcheck가 /health를 부른다
_PUBLIC_PATHS = frozenset({"/health"})
_DOCS_PATHS = frozenset({_DOCS_URL, _DOCS_OAUTH2_REDIRECT_URL, _REDOC_URL, _OPENAPI_URL})
# 404·405 등 프레임워크 실패도 다른 실패와 같은 {"reason": "NOT_FOUND"} 형식으로 낸다
_REASON_BY_STATUS = {code.value: code.name for code in HTTPStatus}


class _LlmClientType(Protocol):
    def __call__(self, *, api_key: str | None, model: str, max_tokens: int, timeout_s: float) -> LlmClient: ...


# 제공자마다 클라이언트 클래스와 키 항목을 한 표에서 함께 고른다(L10). 키는 Literal 제공자 이름과 같다
_LLM_PROVIDERS: Mapping[str, tuple[_LlmClientType, Callable[[Settings], SecretStr | None]]] = {
    "claude": (ClaudeClient, lambda settings: settings.anthropic_api_key),
    "openai": (OpenAiClient, lambda settings: settings.openai_api_key),
}


def create_app(settings: Settings | None = None) -> FastAPI:
    """Settings를 한 번 읽어 app.state에 두고 라우터를 등록한다. 서비스는 lifespan이 만든다."""
    settings = settings or Settings()  # 값은 환경 변수에서 읽는다
    _check_llm_timeout(settings)
    _configure_logging()
    docs = settings.expose_docs
    app = FastAPI(
        title="runkk-ai",
        lifespan=lifespan,
        docs_url=_DOCS_URL if docs else None,
        redoc_url=_REDOC_URL if docs else None,
        openapi_url=_OPENAPI_URL if docs else None,
        swagger_ui_oauth2_redirect_url=_DOCS_OAUTH2_REDIRECT_URL,
    )
    app.state.settings = settings
    app.add_middleware(
        InternalTokenMiddleware,
        token=settings.internal_token,
        public_paths=(_PUBLIC_PATHS | _DOCS_PATHS) if docs else _PUBLIC_PATHS,
    )
    app.add_exception_handler(RequestValidationError, _invalid_request)
    app.add_exception_handler(StarletteHTTPException, _http_error)
    app.add_exception_handler(Exception, _internal_error)
    app.include_router(HealthRouter)
    app.include_router(CommentRouter)
    app.include_router(FaceRouter)
    app.include_router(RunbodyRouter)
    app.include_router(MonthlyRouter)
    return app


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """만든 자원은 만들 때마다 닫기를 등록한다. 뒤 자원 생성이나 한 자원의 aclose()가 실패해도 나머지는 닫힌다(L1)."""
    settings: Settings = app.state.settings
    async with AsyncExitStack() as resources:
        llm = _build_llm_client(settings)
        resources.push_async_callback(llm.aclose)
        transformer = _build_face_transformer(settings)
        resources.push_async_callback(transformer.aclose)
        app.state.comment_service = CommentService(llm=llm, daily_cap=settings.llm_daily_hard_cap)
        app.state.face_service = FaceTransformService(
            transformer=transformer,
            timeout_s=settings.face_timeout_s,
            max_image_bytes=settings.max_image_bytes,
            max_image_pixels=settings.max_image_pixels,
        )
        # 분석 결과 저장소는 S15P31A207-163에서 analytics 스키마 구현으로 바꾼다.
        # 그 전까지는 재시작하면 비는 메모리 저장소다
        repository = InMemoryResultRepository()
        runbody = RunbodyService(repository)
        resources.push_async_callback(runbody.aclose)
        app.state.runbody_service = runbody
        app.state.monthly_service = MonthlyService(repository)
        logger.info(
            "ai_started llm_provider=%s llm_model=%s llm_key_set=%s face_impl=%s face_url_set=%s"
            " runbody_storage=memory",
            settings.llm_provider,
            settings.llm_model,
            _llm_key(settings) is not None,
            settings.face_impl,
            bool(settings.face_api_url),
        )
        yield


def _check_llm_timeout(settings: Settings) -> None:
    """LLM 호출 제한은 라우터 전체 제한보다 짧아야 SDK 시간 초과를 504 전에 본다(H-22).

    두 값을 모두 아는 곳이 main이라 여기서 확인한다(comment.router.COMMENT_TIMEOUT_S가 유일한 원본).
    """
    if settings.llm_timeout_s >= COMMENT_TIMEOUT_S:
        raise ValueError(
            f"RUNKK_LLM_TIMEOUT_S({settings.llm_timeout_s})는"
            f" 라우터 전체 제한 COMMENT_TIMEOUT_S({COMMENT_TIMEOUT_S})보다 짧아야 한다"
        )


def _configure_logging() -> None:
    """runkk_ai.* 호출 지표 로그(llm_call, face_transform)를 표준 오류로 낸다. uvicorn은 이 로거를 설정하지 않는다."""
    package_logger = logging.getLogger("runkk_ai")
    package_logger.setLevel(logging.INFO)
    if not any(getattr(h, "name", None) == "runkk_ai" for h in package_logger.handlers):
        handler = logging.StreamHandler()
        handler.name = "runkk_ai"
        handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(name)s %(message)s"))
        package_logger.addHandler(handler)


def _llm_key(settings: Settings) -> str | None:
    _, key_of = _LLM_PROVIDERS[settings.llm_provider]
    secret = key_of(settings)
    return secret.get_secret_value() if secret else None


def _build_llm_client(settings: Settings) -> LlmClient:
    client_type, _ = _LLM_PROVIDERS[settings.llm_provider]
    return client_type(
        api_key=_llm_key(settings),
        model=settings.llm_model,
        max_tokens=settings.llm_max_tokens,
        timeout_s=settings.llm_timeout_s,
    )


def _build_face_transformer(settings: Settings) -> FaceTransformer:
    if settings.face_impl == "local":
        # LocalSdModel은 C-011에서 팀 운영 모델로 정하면 모델 적재와 함께 연결한다(부록 E E-04, A-003)
        raise RuntimeError("face_impl=local은 C-011 모델 선정 뒤에 연결한다")
    key = settings.face_api_key.get_secret_value() if settings.face_api_key else None
    return ExternalFaceApi(base_url=settings.face_api_url, api_key=key, timeout_s=settings.face_timeout_s)


async def _invalid_request(request: Request, exc: Exception) -> JSONResponse:
    """422 본문에 입력값을 되돌려 주지 않는다(회원 식별 항목이 섞여 와도 응답·로그로 새지 않게)."""
    return JSONResponse(status_code=status.HTTP_422_UNPROCESSABLE_CONTENT, content={"reason": "INVALID_REQUEST"})


async def _internal_error(request: Request, exc: Exception) -> JSONResponse:
    """처리 안 된 예외도 {"reason"} 형식으로 맞춘다(N3). 요청 내용이 섞일 수 있어 예외 클래스 이름만 남긴다."""
    logger.error("unhandled_error type=%s path=%s", type(exc).__name__, request.url.path)
    return JSONResponse(status_code=500, content={"reason": "INTERNAL_ERROR"})


async def _http_error(request: Request, exc: Exception) -> JSONResponse:
    """404·405 같은 프레임워크 실패도 {"reason"} 형식으로 맞춘다(L13). Allow 같은 헤더는 그대로 둔다."""
    http_exc = cast(StarletteHTTPException, exc)
    reason = _REASON_BY_STATUS.get(http_exc.status_code, "HTTP_ERROR")
    return JSONResponse(status_code=http_exc.status_code, content={"reason": reason}, headers=http_exc.headers)
