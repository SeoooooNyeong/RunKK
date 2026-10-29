package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import java.time.LocalDate

/**
 * 배틀런 화면(C-1·C-9, D-268). [segment]는 랜덤 대결 | 친구 대결, [distance]는 고른 거리(처음엔 서버 기본 대결 거리, 못 읽으면 5km),
 * [cards]는 1·3·5·10km 카드마다 내 최고 기록과 날짜(없으면 "기록 없음"이고 고를 수 있다)다.
 */
data class BattleModeUiState(
    val segment: Segment = Segment.RANDOM,
    val distance: Distance = MatchUiState.DEFAULT_DISTANCE,
    val cards: List<DistanceCard> = MatchUiState.DISTANCES.map { DistanceCard(it, best = null, recordedOn = null) },
    val isConsentSheetVisible: Boolean = false,
    val message: UiText? = null
) {
    enum class Segment { RANDOM, FRIEND }

    /** 거리 카드 한 장. */
    data class DistanceCard(val distance: Distance, val best: Duration?, val recordedOn: LocalDate?)
}
