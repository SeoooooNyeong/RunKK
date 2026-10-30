package com.a207.runkk.app.domain.battle

/**
 * 랜덤 대결 잠금(서버 MatchingService RANKED_ONLY "유효 러닝 3회 뒤 랜덤 대결 가능"). 친구 대결은 티어와 무관하다(D57).
 * 화면은 매칭을 요청하기 전에 이 값으로 잠그고 남은 횟수를 알린다(사용자 요청 2026-10-30).
 */
object RandomBattleGate {
    /** 첫 티어 배치가 일어나는 유효 러닝 횟수. */
    const val PLACEMENT_RUNS = 3

    private const val UNRANKED = "UNRANKED"

    /**
     * 랜덤 대결까지 남은 유효 러닝 수. 티어가 있으면 null(열림), 언랭이면 1 이상, 유효 러닝 수를 모르면 0(남은 수를 모르는 잠금).
     * [tier]를 모르면(프로필을 못 읽음) 잠그지 않는다: 서버가 RANKED_ONLY로 거부하면 매칭 화면이 그 안내를 보인다.
     */
    fun runsLeft(tier: String?, validRuns: Int?): Int? = when {
        tier == null || tier != UNRANKED -> null
        validRuns == null -> 0
        else -> (PLACEMENT_RUNS - validRuns).coerceAtLeast(1)
    }
}
