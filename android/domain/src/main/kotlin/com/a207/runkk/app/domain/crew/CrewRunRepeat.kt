package com.a207.runkk.app.domain.crew

/**
 * 크루런 모집글 반복(서버 crew.CrewRunRepeat, 피그마 F-12f, 계약 contract-W6.md 3절). [WEEKLY]면 서버가 매주 일요일 0시 5분에 다음 주
 * 같은 요일·시각 회차를 만든다.
 */
enum class CrewRunRepeat {
    NONE,
    WEEKLY
}
