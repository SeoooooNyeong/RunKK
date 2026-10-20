"""ai 서버 조립(6.1.16, D-60). main → core·comment·face 한 방향이고 어느 기능 모듈도 이 모듈을 import하지 않는다.

실행: uvicorn --factory runkk_ai.main:create_app
"""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from runkk_ai.comment import ClaudeClient, CommentRouter, CommentService, LlmClient, OpenAiClient
from runkk_ai.core import HealthRouter, Settings, verify_internal_token
from runkk_ai.face import ExternalFaceApi, FaceRouter, FaceTransformer, FaceTransformService

logger = logging.getLogger(__name__)


def create_app(settings: Settings | None = None) -> FastAPI:
    """Settings를 한 번 읽어 app.state에 두고 라우터를 등록한다. 서비스는 lifespan이 만든다."""
    settings = settings or Settings()  # 값은 환경 변수에서 읽는다
    _configure_logging()
    docs = settings.expose_docs
    app = FastAPI(
        title="runkk-ai",
        lifespan=lifespan,
        docs_url="/docs" if docs else None,
        redoc_url="/redoc" if docs else None,
        openapi_url="/openapi.json" if docs else None,
    )
    app.state.settings = settings
    app.add_exception_handler(RequestValidationError, _invalid_request)
    app.include_router(HealthRouter)
    protected = [Depends(verify_internal_token)]
    app.include_router(CommentRouter, dependencies=protected)
    app.include_router(FaceRouter, dependencies=protected)
    return app


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings: Settings = app.state.settings
    llm = _build_llm_client(settings)
    transformer = _build_face_transformer(settings)
    app.state.comment_service = CommentService(llm=llm, daily_cap=settings.llm_daily_hard_cap)
    app.state.face_service = FaceTransformService(
        transformer=transformer, timeout_s=settings.face_timeout_s, max_image_bytes=settings.max_image_bytes
    )
    logger.info(
        "ai_started llm_provider=%s llm_model=%s llm_key_set=%s face_impl=%s face_url_set=%s",
        settings.llm_provider,
        settings.llm_model,
        _llm_key(settings) is not None,
        settings.face_impl,
        bool(settings.face_api_url),
    )
    try:
        yield
    finally:
        await llm.aclose()
        await transformer.aclose()


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
    secret = settings.anthropic_api_key if settings.llm_provider == "claude" else settings.openai_api_key
    return secret.get_secret_value() if secret else None


def _build_llm_client(settings: Settings) -> LlmClient:
    client_type = ClaudeClient if settings.llm_provider == "claude" else OpenAiClient
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
