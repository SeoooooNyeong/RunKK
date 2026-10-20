"""집계값만으로 프롬프트를 만든다. 이름·식별자·위치를 받지 않는다(NFR-024)."""

from __future__ import annotations

from runkk_ai.comment.schemas import COMMENT_MAX_CHARS, SummaryIn

_DAY_NAMES = {
    "MONDAY": "월요일",
    "TUESDAY": "화요일",
    "WEDNESDAY": "수요일",
    "THURSDAY": "목요일",
    "FRIDAY": "금요일",
    "SATURDAY": "토요일",
    "SUNDAY": "일요일",
}


class PromptBuilder:
    def build(self, summary: SummaryIn) -> str:
        band_start = summary.frequentHourBand * 3
        lines = [
            "당신은 러닝 앱의 코치입니다. 아래 최근 러닝 요약을 보고 러너에게 건넬 격려 코멘트를 씁니다.",
            f"- 최근 유효 러닝 횟수: {summary.runCount}회",
            f"- 평균 러닝 점수(VDOT): {summary.averageScore:.1f}",
            f"- 직전 기간 대비 평균 거리 변화: {_signed(summary.distanceChangeMeters)}m",
            f"- 직전 기간 대비 평균 페이스: {_pace_text(summary.paceChangeSeconds)}",
            f"- 자주 달린 요일: {_DAY_NAMES[summary.frequentDay]}",
            f"- 자주 달린 시간대: {band_start}~{band_start + 3}시",
            "",
            f"규칙: 한국어 존댓말로 한두 문장, 공백 포함 {COMMENT_MAX_CHARS}자 이내.",
            "숫자를 지어내지 말고 위 값만 쓴다. 의학적 진단·부상 판단은 하지 않는다.",
            "코멘트 문장만 출력한다(인사말·따옴표·머리말 없이).",
        ]
        return "\n".join(lines)


def _signed(value: int) -> str:
    return f"+{value}" if value > 0 else str(value)


def _pace_text(seconds: int) -> str:
    if seconds < 0:
        return f"km당 {-seconds}초 빨라짐"
    if seconds > 0:
        return f"km당 {seconds}초 느려짐"
    return "같음"
