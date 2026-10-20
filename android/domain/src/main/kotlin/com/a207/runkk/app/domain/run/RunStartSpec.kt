package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.util.UUID

/**
 * 러닝 한 회의 시작 조건(6.1.14). recordId는 앱이 만든 UUID다(D-11).
 * todayPlan: 자유런이면 ui.run이 RecordRepository.todayPlan()에서 채우고, 대결은 조정자가 대결 거리를 목표 거리로 넣는다.
 */
data class RunStartSpec(
    val mode: RunMode,
    val cardKind: CardKind,
    val startDevice: StartDevice = StartDevice.PHONE,
    val battleId: Long? = null,
    val crewRunPostId: Long? = null,
    val recordId: String = UUID.randomUUID().toString(),
    val todayPlan: PlanSnapshot? = null
) {
    /** 시작 기기(FR-183: 워치에서 눌러도 폰이 측정한다). */
    enum class StartDevice {
        PHONE,
        WATCH
    }
}

/** 오늘 계획(목표 거리, 목표 페이스). */
data class PlanSnapshot(val targetDistance: Distance?, val targetPace: Pace?)

/** 크루런 순위(순위, 인원). */
data class CrewRank(val rank: Int, val headcount: Int) {
    init {
        require(rank in 1..headcount) { "순위는 1..인원: $rank / $headcount" }
    }
}
