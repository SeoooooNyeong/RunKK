"""AI 코치(F5). 이 MR은 도구 3종만 둔다(S15P31A207-192). 대화 API(/coach/chat SSE)는 S15P31A207-193.

coach는 runbody의 결과(저장소·월간 서비스)를 읽기만 한다. runbody·storage·writer는 coach를 모른다.
"""

from runkk_ai.coach.tools import TOOL_SPECS, CoachTools, monthly_digest, session_digest

__all__ = ["TOOL_SPECS", "CoachTools", "monthly_digest", "session_digest"]
