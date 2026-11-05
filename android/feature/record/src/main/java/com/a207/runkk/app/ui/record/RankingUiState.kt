package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Ranking

data class RankingUiState(
    val scope: RankingScope = RankingScope.PERSONAL,
    val period: AggregationPeriod = AggregationPeriod.WEEKLY,
    val isLoading: Boolean = true,
    val ranking: Ranking? = null,
    val error: UiText? = null,
    /** 고른 기간의 집계 기간과 마감까지 남은 시간(랭킹 v3). 읽을 때마다 지금 시각으로 다시 센다. */
    val window: RankingWindow? = null
) {
    /** TOP3 시상대와 4위부터의 목록(FR-290). 랭킹을 읽기 전이면 null. */
    val podium: RankingPodium?
        get() = ranking?.let { RankingPodium.of(it.entries) }
}
