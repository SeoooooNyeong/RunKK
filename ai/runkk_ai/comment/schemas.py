"""코멘트 요청·응답 형식(6.1.16). 회원 식별 항목을 받지 않는다(NFR-024)."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

DayOfWeek = Literal["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]

COMMENT_MAX_CHARS = 120


class SummaryIn(BaseModel):
    """api FitnessSummary의 집계값만. 기록 순번(fromRun·toRun)·회원 식별자는 정의 밖이라 422."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    averageScore: float = Field(ge=0)  # RunningScore(VDOT) 평균
    distanceChangeMeters: int  # 직전 10회 대비 평균 거리 변화(부호 있음)
    paceChangeSeconds: int  # 직전 10회 대비 평균 페이스 변화(초/km, 음수 = 빨라짐)
    frequentDay: DayOfWeek  # java.time.DayOfWeek 이름
    frequentHourBand: int = Field(ge=0, le=7)  # 3시간 단위 0~7
    runCount: int = Field(ge=1, le=10)  # 요약에 쓴 최근 유효 러닝 횟수


class CommentOut(BaseModel):
    text: str = Field(min_length=1, max_length=COMMENT_MAX_CHARS)
