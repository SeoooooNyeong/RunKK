package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.account.PlacementRound

/**
 * 배치고사 완료(FR-281, 피그마 61357:3270·축하 61361:3142~3974). [tier]는 확정 티어(서버 Tier 이름), [rounds]는 센 세 판(판 번호 순),
 * [wins]·[losses]·[draws]는 판정 수다.
 */
data class PlacementCompleteUiState(
    val isLoading: Boolean = true,
    val tier: String? = null,
    val rounds: List<PlacementRound> = emptyList(),
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val error: UiText? = null,
    /** 확정된 Runimal 유형 이름(없으면 null, Runimal 카드를 숨긴다)과 그 유형의 한 줄 설명(못 읽었으면 null). */
    val runimalType: String? = null,
    val runimalDescription: String? = null
) {
    /** 표 머리 「n승 n패」(무승부가 있으면 「n승 n패 n무」, FR-281). */
    val recordLabel: UiText
        get() = if (draws > 0) {
            UiText.Res(R.string.placement_complete_record_draw, listOf(wins, losses, draws))
        } else {
            UiText.Res(R.string.placement_complete_record, listOf(wins, losses))
        }
}
