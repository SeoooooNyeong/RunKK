package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Ranking

data class RankingUiState(
    val scope: RankingScope = RankingScope.PERSONAL,
    val period: AggregationPeriod = AggregationPeriod.WEEKLY,
    val isLoading: Boolean = true,
    val ranking: Ranking? = null,
    val error: UiText? = null
)
