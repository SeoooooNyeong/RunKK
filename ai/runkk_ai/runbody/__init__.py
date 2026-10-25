"""RUN바디 1회 분석(F1·F3). 라우터 → 서비스 → 엔진·저장소 → 점수·코칭·피드백 → 지표·전처리 한 방향.

Settings를 import하지 않는다(D-60). main이 값을 생성자로 넘긴다.
"""

from runkk_ai.runbody.engine import analyze_session
from runkk_ai.runbody.repository import InMemoryResultRepository, ResultRepository, SessionRecord
from runkk_ai.runbody.router import ANALYSIS_BODY_MAX_BYTES, RunbodyRouter, get_runbody_service
from runkk_ai.runbody.schemas import (
    ENGINE_VERSION,
    AnalyzeAccepted,
    SessionIn,
    SessionResultOut,
    StatusOut,
)
from runkk_ai.runbody.service import Analyzer, RunbodyService

__all__ = [
    "ANALYSIS_BODY_MAX_BYTES",
    "ENGINE_VERSION",
    "AnalyzeAccepted",
    "Analyzer",
    "InMemoryResultRepository",
    "ResultRepository",
    "RunbodyRouter",
    "RunbodyService",
    "SessionIn",
    "SessionRecord",
    "SessionResultOut",
    "StatusOut",
    "analyze_session",
    "get_runbody_service",
]
