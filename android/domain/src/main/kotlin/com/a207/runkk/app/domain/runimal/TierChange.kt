package com.a207.runkk.app.domain.runimal

/**
 * 티어 재평가의 변화(직전 티어 → 이번 티어). 재평가 화면이 결과 문장(올라갔어요·지켰어요·티어가 됐어요·티어예요)과 「이번 평가」 값을
 * 고른다. 직전 티어를 모르면(과거 기록·옛 서버) [UNKNOWN]이다.
 */
enum class TierChange {
    UP,
    KEEP,
    DOWN,
    UNKNOWN;

    companion object {
        /** 확정 티어 순서(낮은 쪽부터). 배치 중(UNRANKED)과 모르는 이름은 순서가 없다. */
        private val RANKED = listOf("BRONZE", "SILVER", "GOLD", "PLATINUM", "MASTER")

        /** [previous]·[current]는 서버 Tier 이름이다. 둘 중 하나라도 순서가 없으면 [UNKNOWN]. */
        fun of(previous: String?, current: String): TierChange {
            val from = RANKED.indexOf(previous)
            val to = RANKED.indexOf(current)
            return when {
                from < 0 || to < 0 -> UNKNOWN
                to > from -> UP
                to < from -> DOWN
                else -> KEEP
            }
        }
    }
}
