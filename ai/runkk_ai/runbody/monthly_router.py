"""월간 RUN바디 엔드포인트(F2). GET /users/{userId}/months/{yearMonth} → 200 MonthlyOut / 422 INVALID_REQUEST.

러닝이 없는 달도 200(sessionCount 0)으로 준다. 앱이 빈 달 화면을 그리면 되고 404를 따로 다루지 않게 한다.
"""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, Path, Request

from runkk_ai.runbody.monthly_service import MonthlyService
from runkk_ai.runbody.schemas import MonthlyOut

MonthlyRouter = APIRouter(prefix="/users", tags=["runbody"])

UserId = Annotated[str, Path(pattern=r"^[A-Za-z0-9_\-:.]{1,64}$")]
YearMonth = Annotated[str, Path(pattern=r"^20\d{2}-(0[1-9]|1[0-2])$", examples=["2026-10"])]


def get_monthly_service(request: Request) -> MonthlyService:
    service: MonthlyService = request.app.state.monthly_service
    return service


@MonthlyRouter.get("/{user_id}/months/{year_month}", response_model=MonthlyOut)
async def get_month(
    user_id: UserId,
    year_month: YearMonth,
    service: Annotated[MonthlyService, Depends(get_monthly_service)],
) -> MonthlyOut:
    return await service.month(user_id, year_month)
