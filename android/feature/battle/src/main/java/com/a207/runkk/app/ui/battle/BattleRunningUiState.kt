package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.live.BattleGuidance
import java.time.Duration

data class BattleRunningUiState(
    val battleId: Long = 0,
    val opponentName: String = "",
    val distance: Distance = Distance(DEFAULT_METERS),
    val kind: OpponentKind = OpponentKind.RANDOM,
    val phase: BattlePhase = BattlePhase.STARTING,
    /** 측정 시작까지 남은 초(COUNTDOWN). */
    val countdown: Long? = null,
    /** 나 − 상대(m), 앞서면 양수. 첫 진행값 전이면 null. */
    val gapMeters: Int? = null,
    val opponentDistance: Distance? = null,
    /** 내 누적 거리·경과·페이스(조정자가 옮긴 측정 진행값). */
    val myDistance: Distance = Distance.ZERO,
    val elapsed: Duration = Duration.ZERO,
    val currentPace: Pace? = null,
    val averagePace: Pace? = null,
    /** 랜덤 대결 상대 연결 상태 문구(FR-088). 기록 상대·첫 상대 진행값 전이면 null. */
    val connectionLabel: UiText? = null,
    /** 화면 잠금(FR-182). 잠기면 해제 버튼만 누를 수 있다. */
    val isLocked: Boolean = false,
    /** 방금 바뀐 앞뒤 순서 안내(잠깐 보인다, FR-188). */
    val guidance: BattleGuidance.Kind? = null,
    /** 이 화면에서 본 추월 횟수. 공식 횟수는 결과 비교표(서버)다. */
    val overtakes: Int = 0,
    /** 먼저 도착한 사람(종료 카운트다운, FR-107). */
    val arrivedName: String? = null,
    val closingSeconds: Long? = null,
    /** 종료 카운트다운이 시작될 때의 남은 초(보조 기술에 한 번 알리는 제한 시간, 매초 읽지 않게 고정). */
    val closingLimitSeconds: Long? = null,
    val isConfirmingForfeit: Boolean = false,
    val hint: UiText? = null,
    val error: UiText? = null
) {
    /** 측정 중인가(진행·종료 카운트다운). 잠금 버튼은 이때만 보인다. */
    val isMeasuring: Boolean get() = phase == BattlePhase.RUNNING || phase == BattlePhase.CLOSING

    private companion object {
        const val DEFAULT_METERS = 5_000L
    }
}
