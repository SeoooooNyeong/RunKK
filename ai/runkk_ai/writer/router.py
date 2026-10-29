"""운영자가 부르는 writer 모델 관리 API(S15P31A207-197).

앱·api가 아니라 운영자가 직접 부른다(다른 경로와 같은 X-Internal-Token으로 막혀 있다).

    curl -X POST localhost:8000/models -H "X-Internal-Token: $T" -H 'Content-Type: application/json' \
      -d '{"modelId":"v2-qwen3-1.7b","baseUrl":"http://writer-v2:8080","note":"통과율 0.97"}'
    curl -X POST localhost:8000/models/v2-qwen3-1.7b/activate -H "X-Internal-Token: $T"
    curl -X POST localhost:8000/models/rollback -H "X-Internal-Token: $T"
"""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, Request, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, field_validator

from runkk_ai.writer.registry import (
    BASE_URL_MAX,
    MODEL_ID_MAX,
    NOTE_MAX,
    ModelNotFound,
    ModelRegistry,
    NoPreviousModel,
    WriterModel,
)

ModelsRouter = APIRouter(prefix="/models", tags=["models"])
_SCHEMES = ("http://", "https://")


class RegisterIn(BaseModel):
    modelId: str = Field(min_length=1, max_length=MODEL_ID_MAX)
    baseUrl: str = Field(min_length=1, max_length=BASE_URL_MAX)
    note: str | None = Field(default=None, max_length=NOTE_MAX)

    @field_validator("baseUrl")
    @classmethod
    def _http_only(cls, value: str) -> str:
        if not value.startswith(_SCHEMES):
            raise ValueError("baseUrl은 http:// 또는 https:// 로 시작해야 한다")
        return value.rstrip("/")


class ModelOut(BaseModel):
    modelId: str
    baseUrl: str
    note: str | None
    active: bool
    registeredAt: str
    activatedAt: str | None

    @classmethod
    def of(cls, model: WriterModel) -> ModelOut:
        return cls(
            modelId=model.model_id,
            baseUrl=model.base_url,
            note=model.note,
            active=model.active,
            registeredAt=model.registered_at.isoformat(),
            activatedAt=model.activated_at.isoformat() if model.activated_at else None,
        )


class ModelsOut(BaseModel):
    models: list[ModelOut]
    active: str | None


def get_model_registry(request: Request) -> ModelRegistry:
    registry: ModelRegistry = request.app.state.model_registry
    return registry


Registry = Annotated[ModelRegistry, Depends(get_model_registry)]


def _failure(status_code: int, reason: str) -> JSONResponse:
    return JSONResponse(status_code=status_code, content={"reason": reason})


@ModelsRouter.get("", response_model=ModelsOut)
async def list_models(registry: Registry) -> ModelsOut:
    models = await registry.models()
    active = next((m.model_id for m in models if m.active), None)
    return ModelsOut(models=[ModelOut.of(m) for m in models], active=active)


@ModelsRouter.post("", response_model=ModelOut, status_code=status.HTTP_201_CREATED)
async def register_model(body: RegisterIn, registry: Registry) -> ModelOut:
    return ModelOut.of(await registry.register(body.modelId, body.baseUrl, body.note))


@ModelsRouter.post(
    "/rollback",
    response_model=None,
    responses={status.HTTP_409_CONFLICT: {"description": "NO_PREVIOUS_MODEL"}},
)
async def rollback(registry: Registry) -> ModelOut | JSONResponse:
    try:
        return ModelOut.of(await registry.rollback())
    except NoPreviousModel:
        return _failure(status.HTTP_409_CONFLICT, "NO_PREVIOUS_MODEL")


@ModelsRouter.post(
    "/{model_id}/activate",
    response_model=None,
    responses={status.HTTP_404_NOT_FOUND: {"description": "MODEL_NOT_FOUND"}},
)
async def activate(model_id: str, registry: Registry) -> ModelOut | JSONResponse:
    try:
        return ModelOut.of(await registry.activate(model_id))
    except ModelNotFound:
        return _failure(status.HTTP_404_NOT_FOUND, "MODEL_NOT_FOUND")
