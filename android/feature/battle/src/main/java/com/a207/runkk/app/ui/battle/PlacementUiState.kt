package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.account.PlacementProgress

/**
 * 배치고사 진행 화면(FR-279, 피그마 61355:3061·61355:3230). [progress]는 본인 배치 진행(못 읽으면 null이고 [error]를 보이며 시작할 수 없다),
 * [distance]는 고른 거리(처음엔 기본 대결 거리)다.
 */
data class PlacementUiState(
    val isLoading: Boolean = true,
    val progress: PlacementProgress? = null,
    val distance: Distance = MatchUiState.DEFAULT_DISTANCE,
    val isConsentSheetVisible: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null
) {
    /** 이번 판 번호(「배치고사 n판 시작」). 진행을 모르면 null. */
    val round: Int? get() = progress?.let { it.nextRound ?: (it.countedRounds + 1) }

    /** 진행을 읽었을 때만 시작할 수 있다(TC-5876). */
    val canStart: Boolean get() = progress != null && !isLoading
}
