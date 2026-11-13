package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Ranking

data class RankingUiState(
    val scope: RankingScope = RankingScope.PERSONAL,
    val period: AggregationPeriod = AggregationPeriod.WEEKLY,
    val isLoading: Boolean = true,
    val ranking: Ranking? = null,
    val error: UiText? = null,
    /** 고른 기간의 집계 기간과 마감까지 남은 시간(랭킹 v3). 읽을 때마다 지금 시각으로 다시 센다. */
    val window: RankingWindow? = null,
    /** 받은 줄 얼굴 그림(얼굴 키 → 층, S15P31A207-300). 받지 못한 얼굴은 빈 목록(기본 머리)이고, 아직 없는 셀카 키는 받는 중이다. */
    val faces: Map<String, List<FaceLayer>> = emptyMap()
) {
    /** TOP3 시상대와 4위부터의 목록(FR-290). 랭킹을 읽기 전이면 null. */
    val podium: RankingPodium?
        get() = ranking?.let { RankingPodium.of(it.entries) }
}
