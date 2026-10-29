"""DELETE /users/{userId} → 200 {저장소별 지운 수} / 503 STORAGE_UNAVAILABLE / 504 STORAGE_TIMEOUT.

멱등이다: 없는 사용자나 이미 지운 사용자도 200에 0이다. api는 탈퇴 리스너와 파기 배치에서 부른다.
"""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, Path, Request, status
from fastapi.responses import JSONResponse

from runkk_ai.privacy.service import PurgeError, WithdrawalService

PrivacyRouter = APIRouter(tags=["privacy"])

UserId = Annotated[str, Path(pattern=r"^[A-Za-z0-9_-]{1,128}$")]

_STATUS_BY_REASON = {
    "STORAGE_UNAVAILABLE": status.HTTP_503_SERVICE_UNAVAILABLE,
    "STORAGE_TIMEOUT": status.HTTP_504_GATEWAY_TIMEOUT,
}


def get_withdrawal_service(request: Request) -> WithdrawalService:
    service: WithdrawalService = request.app.state.withdrawal_service
    return service


@PrivacyRouter.delete(
    "/users/{user_id}",
    response_model=dict[str, int],
    responses={
        status.HTTP_503_SERVICE_UNAVAILABLE: {"description": "STORAGE_UNAVAILABLE"},
        status.HTTP_504_GATEWAY_TIMEOUT: {"description": "STORAGE_TIMEOUT"},
    },
)
async def purge_user(
    user_id: UserId, service: Annotated[WithdrawalService, Depends(get_withdrawal_service)]
) -> dict[str, int] | JSONResponse:
    try:
        return await service.purge(user_id)
    except PurgeError as error:
        return JSONResponse(status_code=_STATUS_BY_REASON[error.reason], content={"reason": error.reason})
