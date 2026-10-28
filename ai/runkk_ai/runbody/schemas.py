"""RUN바디 1회 분석 요청·응답 형식(F1·F3). 필드는 camelCase로 api(Spring) record와 같은 표기를 쓴다.

요청에는 회원 식별 항목(이름·이메일)이 없다. userId는 같은 사용자의 이전 결과를 찾는 불투명 키로만 쓴다.
"""

from __future__ import annotations

from datetime import date, datetime
from typing import Any, Literal

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field

ENGINE_VERSION = "0.2"
SCHEMA_VERSION = 2

# 1초 그리드를 만드는 길이 상한. 12시간이면 그리드 4만여 행이라 메모리가 넉넉하다
MAX_ACTIVE_MS = 12 * 60 * 60 * 1000
MAX_SAMPLES = 100_000
_ID_PATTERN = r"^[A-Za-z0-9_\-:.]{1,64}$"

AxisName = Literal["intensity", "endurance", "stability", "form", "fitness"]
AXIS_NAMES: tuple[AxisName, ...] = ("intensity", "endurance", "stability", "form", "fitness")
AxisLevel = Literal["good", "mid", "warn", "none"]
CoachingFlag = Literal["warmup_missing", "positive_split", "overstride", "recovery_slower", "acwr_high"]
QualityFlag = Literal[
    "gps_lock_slow",
    "pre_gps_distance_estimated",
    "short_run",
    "no_heart_rate",
    "no_gps",
    "no_cadence",
]
AnalysisStatus = Literal["pending", "done", "failed"]

# 엔진 안에서 쓰는 지표 묶음. 키는 snake_case이고 응답으로 나갈 때 camelCase로 바꾼다
Metrics = dict[str, Any]


class _In(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class SampleIn(_In):
    """워치 센서 한 점. 필드마다 기록 시각이 달라도 된다(엔진이 1초 그리드로 맞춘다). distance는 구간 증가량(m)."""

    tMs: int = Field(ge=0)
    heartRate: float | None = Field(default=None, gt=0, lt=260, allow_inf_nan=False)
    speed: float | None = Field(default=None, ge=0, lt=15, allow_inf_nan=False)
    cadence: float | None = Field(default=None, ge=0, lt=300, allow_inf_nan=False)
    distance: float | None = Field(default=None, ge=0, lt=100, allow_inf_nan=False)


class GpsIn(_In):
    tMs: int = Field(ge=0)
    lat: float = Field(ge=-90, le=90, allow_inf_nan=False)
    lon: float = Field(ge=-180, le=180, allow_inf_nan=False)
    alt: float | None = Field(default=None, allow_inf_nan=False)
    acc: float | None = Field(default=None, ge=0, allow_inf_nan=False)


class RecoveryHeartRateIn(_In):
    """운동 종료 뒤 회복 심박. tMs는 종료 시각 기준."""

    tMs: int = Field(ge=0)
    heartRate: float = Field(gt=0, lt=260, allow_inf_nan=False)


class ProfileIn(_In):
    birthYear: int = Field(ge=1900, le=2100)
    # 관측한 최고 심박. api가 알고 있으면 넘긴다. 없으면 220 - 나이
    hrMax: int | None = Field(default=None, ge=120, le=230)


class SessionIn(_In):
    """POST /sessions/{sessionId}/analyze 본문. 경로의 sessionId와 같아야 한다."""

    sessionId: str = Field(pattern=_ID_PATTERN)
    userId: str = Field(pattern=_ID_PATTERN)
    source: Literal["watch", "phone"] = "watch"
    startedAt: AwareDatetime
    endedAt: AwareDatetime | None = None
    activeMs: int = Field(gt=0, le=MAX_ACTIVE_MS)
    distanceMeters: float = Field(gt=0, lt=200_000, allow_inf_nan=False)
    profile: ProfileIn
    samples: list[SampleIn] = Field(min_length=1, max_length=MAX_SAMPLES)
    gps: list[GpsIn] = Field(default_factory=list, max_length=MAX_SAMPLES)
    recoveryHeartRates: list[RecoveryHeartRateIn] = Field(default_factory=list, max_length=1_000)


class AnalyzeAccepted(BaseModel):
    sessionId: str
    status: AnalysisStatus
    engineVersion: str = ENGINE_VERSION


class StatusOut(BaseModel):
    sessionId: str
    status: AnalysisStatus
    updatedAt: datetime
    errorCode: str | None = None


class SummaryOut(BaseModel):
    distanceMeters: int
    activeMs: int
    paceSecondsPerKm: int
    avgHeartRate: int | None
    maxHeartRate: int | None
    cadenceMedian: int | None
    recoveryHeartRate1m: int | None  # 종료 1분 뒤 심박 변화(음수일수록 회복이 빠름)
    startedAt: datetime
    source: Literal["watch", "phone"]


class AxisOut(BaseModel):
    """5각 점수 한 축. label·level은 앱이 그대로 보여 준다(앱이 임계값을 다시 판단하지 않는다)."""

    score: int | None = Field(ge=0, le=100)
    ref: int
    value: float | None
    label: str
    level: AxisLevel


class LapOut(BaseModel):
    km: float
    paceSeconds: int
    avgHeartRate: int | None
    partial: bool = False


class PrevOut(BaseModel):
    sessionId: str
    distanceMeters: int
    paceSecondsPerKm: int
    recoveryHeartRate1m: int | None


class FeedbackOut(BaseModel):
    headline: str
    good: str
    fix: str
    next: str
    moreFixes: list[str]
    ruleId: str
    goodRuleId: str
    source: Literal["template", "writer"] = "template"  # writer: 자체 모델이 다듬은 문장(S15P31A207-196)
    modelVersion: str | None = None  # writer 모델 버전. 템플릿이면 null


class CoachingOut(BaseModel):
    flags: list[CoachingFlag]
    detail: dict[str, Any]  # warmup·split·overstride·recovery·acwr 판단 근거(camelCase)


class SessionResultOut(BaseModel):
    """GET /sessions/{sessionId} 200 본문(schemaVersion 2)."""

    sessionId: str
    userId: str
    schemaVersion: int = SCHEMA_VERSION
    engineVersion: str = ENGINE_VERSION
    summary: SummaryOut
    axes: dict[AxisName, AxisOut]
    metrics: dict[str, Any]  # 축별 지표 원값(camelCase). 상세 화면용
    coaching: CoachingOut
    laps: list[LapOut]
    prev: PrevOut | None
    feedback: FeedbackOut
    qualityFlags: list[QualityFlag]


# ---------- F2 월간 RUN바디 ----------

AcwrLevel = Literal["low", "normal", "high"]


class MonthlyAxisOut(BaseModel):
    """한 달 동안 점수가 있는 세션들의 평균(반올림). 점수가 하나도 없으면 average는 None."""

    average: int | None
    count: int
    ref: int


class BestOut(BaseModel):
    value: int  # 초(1km·2km), m(최장), bpm 변화(회복)
    sessionId: str
    runDate: date  # 한국 시간 기준 러닝 날짜


class BestsOut(BaseModel):
    fastestKmSeconds: BestOut | None
    fastest2kmSeconds: BestOut | None  # 연속한 온전한 1 km 랩 두 개의 합
    longestRunMeters: BestOut | None
    bestRecoveryHeartRate1m: BestOut | None  # 가장 많이 떨어진(가장 작은) 값


class WeekOut(BaseModel):
    weekStart: date  # 월요일(한국 시간)
    distanceMeters: int
    sessionCount: int


class TimeOfDayOut(BaseModel):
    night: int  # 18:00~05:59 시작(한국 시간)
    day: int


class LoadOut(BaseModel):
    """최근 부하(ACWR = 7일 거리 ÷ 28일 주평균 거리). 첫 러닝부터 28일이 지나야 켠다."""

    available: bool
    ratio: float | None
    level: AcwrLevel | None
    acuteMeters: int  # 최근 7일
    chronicWeeklyMeters: int  # 최근 28일 ÷ 4
    availableFrom: date | None  # available이 false일 때 켜지는 날짜


class BaselineOut(BaseModel):
    """개인 기준값: 최근 러닝(최대 10회)의 평균. 3회부터 쓴다."""

    available: bool
    sessionCount: int
    needs: int  # 기준값까지 남은 러닝 수
    paceSecondsPerKm: int | None
    avgHeartRate: int | None
    cadenceMedian: int | None
    recoveryHeartRate1m: int | None


class AvatarInputsOut(BaseModel):
    """백엔드가 체형 단계를 정할 때 쓰는 입력값. 단계 판단은 백엔드가 한다."""

    distance14dMeters: int
    sessions14d: int


class MonthlyOut(BaseModel):
    """GET /users/{userId}/months/{yearMonth} 200 본문. 이번 달이면 asOf(지금)까지, 지난달이면 월말까지 계산한다."""

    userId: str
    yearMonth: str
    asOf: datetime
    engineVersion: str = ENGINE_VERSION
    sessionCount: int
    distanceMeters: int
    activeMs: int
    paceSecondsPerKm: int | None
    avgHeartRate: int | None
    axes: dict[AxisName, MonthlyAxisOut]
    bests: BestsOut
    weeks: list[WeekOut]
    timeOfDay: TimeOfDayOut
    load: LoadOut
    baseline: BaselineOut
    avatarInputs: AvatarInputsOut
