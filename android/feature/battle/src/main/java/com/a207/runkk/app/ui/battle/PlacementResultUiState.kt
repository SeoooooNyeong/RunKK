package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.account.PlacementProgress
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.PlacementRoundState
import com.a207.runkk.app.domain.record.RecordDetail

/**
 * 배치고사 판 결과(FR-280, 피그마 61357:3020). [detail]은 이 판 본인 기록 상세(아직 없으면 null), [state]는 판 셈 상태(모르면 null =
 * 정리 중), [progress]는 본인 배치 진행, [isStillPending]은 30초 동안 다시 읽어도 정리 중인 상태다. [progressError]는 진행을 끝내
 * 읽지 못했을 때의 안내(진행 카드 자리에 다시 시도와 함께, PL2-A2)다.
 */
data class PlacementResultUiState(
    val isLoading: Boolean = true,
    val battleId: Long = 0,
    val result: BattleResult? = null,
    val detail: RecordDetail? = null,
    val state: PlacementRoundState? = null,
    val progress: PlacementProgress? = null,
    val isStillPending: Boolean = false,
    val error: UiText? = null,
    val progressError: UiText? = null
) {
    val verdict: Verdict? get() = Verdict.of(result?.verdict)

    /** 판 셈이 아직 정해지지 않았다(PENDING이거나 모르는 값). */
    val isPending: Boolean get() = result != null && state != PlacementRoundState.COUNTED &&
        state != PlacementRoundState.NOT_COUNTED

    /** 진행 카드 아래 안내: 세지 않은 판(J24-20) 또는 30초 뒤에도 정리 중(D-501). */
    val notice: UiText?
        get() = when {
            state == PlacementRoundState.NOT_COUNTED -> UiText.Res(R.string.placement_result_not_counted)
            isStillPending -> UiText.Res(R.string.placement_result_still_pending)
            else -> null
        }
}
