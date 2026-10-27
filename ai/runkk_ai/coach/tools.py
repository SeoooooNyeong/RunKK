"""AI 코치 도구 3종(F5, S15P31A207-192): getSessionResult · getMonthlyResult · getBaseline.

API 모델이 사용자 질문을 보고 도구를 고르면 서버가 여기서 값을 꺼내 짧은 JSON으로 돌려준다.
- 사용자 ID는 모델이 정하지 않는다. api가 인증한 사용자 ID를 서버가 넣는다(다른 사람 기록을 볼 수 없다).
- 응답은 토큰을 아끼려고 줄인다. 페이스는 6'05" 형식, 날짜는 한국 시간으로 미리 바꾼다(모델이 계산하지 않게).
- 저장소 규약(ResultRepository) 위에서만 읽는다. 메모리 저장소든 analytics 스키마든 같은 결과가 나온다.
- 실패는 예외 대신 {"error": 코드}로 돌려준다. 모델이 그 사실을 사용자에게 말하면 된다.
"""

from __future__ import annotations

from collections.abc import Awaitable, Callable, Mapping
from datetime import UTC, datetime, timedelta
from typing import Any

from runkk_ai.runbody import MonthlyService, ResultRepository, SessionResultOut
from runkk_ai.runbody.load import CHRONIC_DAYS, baseline, kst_date, recent_load
from runkk_ai.runbody.schemas import BestOut, MonthlyOut
from runkk_ai.writer.brief import pace_text

RECENT_RUNS = 5  # getBaseline이 함께 주는 최근 러닝 수
BASELINE_RUNS = 10
KST = timedelta(hours=9)
Tool = Callable[[str, Mapping[str, Any]], Awaitable[dict[str, Any]]]

# Anthropic tool 형식(이름·설명·입력). 설명은 모델이 언제 어떤 도구를 부를지 고르는 기준이다
TOOL_SPECS: list[dict[str, Any]] = [
    {
        "name": "getSessionResult",
        "description": (
            "러닝 1회의 분석 결과(거리·페이스·심박·5각 판정·코칭 신호·피드백·지난 러닝 비교)를 가져온다. "
            "sessionId를 비우면 가장 최근 러닝. '어제 러닝 어땠어', '왜 출발이 빨랐다고 해' 같은 질문에 쓴다."
        ),
        "input_schema": {
            "type": "object",
            "properties": {"sessionId": {"type": "string", "description": "러닝 세션 ID. 모르면 비운다"}},
        },
    },
    {
        "name": "getMonthlyResult",
        "description": (
            "한 달 요약(러닝 수·총거리·평균 페이스·5각 평균·최고 기록·주간 거리·최근 부하)을 가져온다. "
            "yearMonth를 비우면 이번 달. '이번 달 얼마나 뛰었어', '지난달이랑 비교해 줘'에 쓴다."
        ),
        "input_schema": {
            "type": "object",
            "properties": {"yearMonth": {"type": "string", "description": "YYYY-MM(예: 2026-10). 비우면 이번 달"}},
        },
    },
    {
        "name": "getBaseline",
        "description": (
            "개인 기준값(최근 10회 평균 페이스·심박·케이던스·1분 회복), 최근 5회 러닝 목록, "
            "최근 부하(ACWR)를 가져온다. "
            "'요즘 체력 좋아졌어', '평소보다 빨랐어', '이번 주 너무 많이 뛰었어'에 쓴다."
        ),
        "input_schema": {"type": "object", "properties": {}},
    },
]


def _now() -> datetime:
    return datetime.now(UTC)


def _when(moment: datetime) -> str:
    return (moment.astimezone(UTC) + KST).strftime("%Y-%m-%d %H:%M")


def _km(meters: int) -> float:
    return round(meters / 1000, 2)


def session_digest(r: SessionResultOut) -> dict[str, Any]:
    s = r.summary
    return {
        "sessionId": r.sessionId,
        "startedAt": _when(s.startedAt),
        "distanceKm": _km(s.distanceMeters),
        "durationMin": round(s.activeMs / 60000),
        "pace": pace_text(s.paceSecondsPerKm),
        "avgHeartRate": s.avgHeartRate,
        "maxHeartRate": s.maxHeartRate,
        "cadence": s.cadenceMedian,
        "recovery1m": s.recoveryHeartRate1m,
        "axes": {name: f"{a.label}({a.score})" if a.score is not None else a.label for name, a in r.axes.items()},
        "flags": list(r.coaching.flags),
        "laps": [pace_text(lap.paceSeconds) for lap in r.laps if not lap.partial],
        "feedback": {k: getattr(r.feedback, k) for k in ("headline", "good", "fix", "next")},
        "prev": (
            {
                "sessionId": r.prev.sessionId,
                "distanceKm": _km(r.prev.distanceMeters),
                "pace": pace_text(r.prev.paceSecondsPerKm),
                "recovery1m": r.prev.recoveryHeartRate1m,
            }
            if r.prev
            else None
        ),
        "qualityFlags": list(r.qualityFlags),
    }


def _best(best: BestOut | None, kind: str) -> dict[str, Any] | None:
    if best is None:
        return None
    value: Any = pace_text(best.value) if kind == "time" else best.value
    return {"value": value, "date": best.runDate.isoformat(), "sessionId": best.sessionId}


def monthly_digest(m: MonthlyOut) -> dict[str, Any]:
    load, base = m.load, m.baseline
    return {
        "yearMonth": m.yearMonth,
        "asOf": _when(m.asOf),
        "sessionCount": m.sessionCount,
        "distanceKm": _km(m.distanceMeters),
        "pace": pace_text(m.paceSecondsPerKm),
        "avgHeartRate": m.avgHeartRate,
        "axesAverage": {name: a.average for name, a in m.axes.items()},
        "bests": {
            "fastest1km": _best(m.bests.fastestKmSeconds, "time"),
            "fastest2km": _best(m.bests.fastest2kmSeconds, "time"),
            "longestRunMeters": _best(m.bests.longestRunMeters, "value"),
            "bestRecovery1m": _best(m.bests.bestRecoveryHeartRate1m, "value"),
        },
        "weeks": [{"weekStart": w.weekStart.isoformat(), "distanceKm": _km(w.distanceMeters)} for w in m.weeks],
        "timeOfDay": {"night": m.timeOfDay.night, "day": m.timeOfDay.day},
        "load": _load(load.available, load.ratio, load.level, load.availableFrom),
        "baseline": _baseline(
            base.available, base.needs, base.paceSecondsPerKm, base.avgHeartRate, base.recoveryHeartRate1m
        ),
    }


def _load(available: bool, ratio: float | None, level: str | None, available_from: Any) -> dict[str, Any]:
    if available:
        return {"acwr": ratio, "level": level}
    return {"acwr": None, "availableFrom": available_from.isoformat() if available_from else None}


def _baseline(available: bool, needs: int, pace: int | None, hr: int | None, recovery: int | None) -> dict[str, Any]:
    if not available:
        return {"available": False, "needsRuns": needs}
    return {"available": True, "pace": pace_text(pace), "avgHeartRate": hr, "recovery1m": recovery}


class CoachTools:
    def __init__(
        self,
        repository: ResultRepository,
        monthly: MonthlyService,
        *,
        clock: Callable[[], datetime] = _now,
    ) -> None:
        self._repository = repository
        self._monthly = monthly
        self._clock = clock
        self._tools: dict[str, Tool] = {
            "getSessionResult": self.get_session_result,
            "getMonthlyResult": self.get_monthly_result,
            "getBaseline": self.get_baseline,
        }

    @property
    def names(self) -> list[str]:
        return list(self._tools)

    async def call(self, user_id: str, name: str, arguments: Mapping[str, Any] | None) -> dict[str, Any]:
        """모델이 고른 도구를 실행한다. 모르는 도구·잘못된 인자는 {"error"}로 돌려준다."""
        tool = self._tools.get(name)
        if tool is None:
            return {"error": "UNKNOWN_TOOL"}
        return await tool(user_id, arguments or {})

    async def get_session_result(self, user_id: str, arguments: Mapping[str, Any]) -> dict[str, Any]:
        session_id = arguments.get("sessionId")
        if session_id:
            record = await self._repository.get(str(session_id))
            # 다른 사용자 기록은 없는 것과 같게 답한다(있다는 사실도 알려 주지 않는다)
            if record is None or record.user_id != user_id or record.result is None:
                return {"error": "SESSION_NOT_FOUND"}
            return session_digest(record.result)
        latest = await self._repository.history(user_id, self._clock() + timedelta(seconds=1), 1)
        if not latest:
            return {"error": "NO_RUNS"}
        return session_digest(latest[-1])

    async def get_monthly_result(self, user_id: str, arguments: Mapping[str, Any]) -> dict[str, Any]:
        year_month = arguments.get("yearMonth") or (self._clock().astimezone(UTC) + KST).strftime("%Y-%m")
        try:
            month = await self._monthly.month(user_id, str(year_month))
        except ValueError:
            return {"error": "INVALID_YEAR_MONTH"}
        return monthly_digest(month)

    async def get_baseline(self, user_id: str, arguments: Mapping[str, Any]) -> dict[str, Any]:
        now = self._clock()
        recent = await self._repository.history(user_id, now + timedelta(seconds=1), BASELINE_RUNS)
        if not recent:
            return {"error": "NO_RUNS"}
        window = await self._repository.results_between(user_id, now - timedelta(days=CHRONIC_DAYS), now)
        first = await self._repository.first_started_at(user_id)
        base, load = baseline(recent), recent_load(window, first, now)
        return {
            "asOf": kst_date(now).isoformat(),
            "baseline": _baseline(
                base.available, base.needs, base.paceSecondsPerKm, base.avgHeartRate, base.recoveryHeartRate1m
            ),
            "recentRuns": [
                {
                    "sessionId": r.sessionId,
                    "startedAt": _when(r.summary.startedAt),
                    "distanceKm": _km(r.summary.distanceMeters),
                    "pace": pace_text(r.summary.paceSecondsPerKm),
                    "recovery1m": r.summary.recoveryHeartRate1m,
                    "headline": r.feedback.headline,
                }
                for r in reversed(recent[-RECENT_RUNS:])
            ],
            "load": _load(load.available, load.ratio, load.level, load.availableFrom),
        }
