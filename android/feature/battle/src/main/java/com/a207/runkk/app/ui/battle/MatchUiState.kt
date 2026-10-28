package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.live.MatchFound

data class MatchUiState(
    val phase: MatchPhase = MatchPhase.CHOOSING,
    val distance: Distance = DEFAULT_DISTANCE,
    /** 대기 시작 뒤 경과 초(시안 "0:07"). */
    val waitingSeconds: Long = 0,
    val match: MatchFound? = null,
    /** 자동 출발까지 남은 초. 시작 예약(BattleStart)을 받기 전이면 null. */
    val startsInSeconds: Long? = null,
    val error: UiText? = null,
    /** 다시 요청해도 같은 거부(언랭)라 화면을 떠날 때까지 매칭 요청을 막는다(배틀 QA 5번). */
    val isStartBlocked: Boolean = false,
    /** 성사 뒤 측정이 시작되지 않고 늦어져 그만두기를 보인다(배틀 QA 2번). */
    val canForfeit: Boolean = false
) {
    companion object {
        /** 랜덤 대결 거리(FR-080). */
        val DISTANCES: List<Distance> = listOf(Distance(1_000), Distance(3_000), Distance(5_000), Distance(10_000))

        /** 기본 대결 거리(FR-109 기본값). 서버의 기본 대결 거리(GET /battles/match)를 읽기 전·읽지 못했을 때 쓴다. */
        val DEFAULT_DISTANCE: Distance = Distance(5_000)
    }
}
