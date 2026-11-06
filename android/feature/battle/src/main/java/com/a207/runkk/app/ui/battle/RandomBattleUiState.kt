package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.TierDisplay
import java.time.Duration

/**
 * 랜덤 대결 화면(FR-285, 피그마 61355:2909). [distance]는 고른 거리(처음엔 기본 대결 거리, 못 읽으면 5km), [bestByDistance]는 거리별 내
 * 최고 기록(없으면 null), [tierDisplay]는 오른쪽 위 본인 티어 배지(못 읽으면 null이고 그리지 않는다)다.
 */
data class RandomBattleUiState(
    val distance: Distance = MatchUiState.DEFAULT_DISTANCE,
    val bestByDistance: Map<Distance, Duration?> = emptyMap(),
    val tierDisplay: TierDisplay? = null,
    val isConsentSheetVisible: Boolean = false,
    val message: UiText? = null
) {
    /** 고른 거리의 내 최고 기록. 없으면 「아직 기록이 없어요」. */
    val best: Duration? get() = bestByDistance[distance]
}
