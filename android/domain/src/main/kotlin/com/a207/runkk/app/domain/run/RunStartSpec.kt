package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.util.UUID

/**
 * 러닝 한 회의 시작 조건(6.1.14). recordId는 앱이 만든 UUID다(D-11).
 * todayPlan: 이번 회의 목표 거리·목표 페이스. 자유런이면 ui.run이 FreeRunGoalUseCase로 고른 목표(코치 계획 따르기 켬 +
 * 오늘 계획 → 오늘 계획, 그 밖 → 자유런 설정 목표 거리, FR-048)를 넣고, 대결은 조정자가 대결 거리를 목표 거리로 넣는다.
 * opponentName: 대결 상대 표시 이름(진행 알림 "vs [상대]", D-258). 자유런은 null이다.
 */
data class RunStartSpec(
    val mode: RunMode,
    val cardKind: CardKind,
    val startDevice: StartDevice = StartDevice.PHONE,
    val battleId: Long? = null,
    val crewRunPostId: Long? = null,
    val recordId: String = UUID.randomUUID().toString(),
    val todayPlan: PlanSnapshot? = null,
    val opponentName: String? = null
) {
    /**
     * 시작 기기(FR-183: 워치에서 눌러도 폰이 측정한다). 워치 단독 측정은 WATCH다(D-378). 확정한 기록(CompletedRun)과 서버 기록에
     * 함께 남는다(W23). 이름은 서버 running.StartDevice와 같다(계약 시험).
     */
    enum class StartDevice {
        PHONE,
        WATCH
    }
}
