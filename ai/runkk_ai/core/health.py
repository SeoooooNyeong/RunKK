"""상태 확인. compose healthcheck가 부르며 토큰 검사를 하지 않는다."""

from __future__ import annotations

from fastapi import APIRouter

HealthRouter = APIRouter(tags=["health"])


@HealthRouter.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}
