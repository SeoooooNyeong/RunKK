package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Growth
import com.a207.runkk.app.domain.record.RecordRepository

// 기록 분석 화면(성장·리포트·랭킹·코치). 모두 RecordRepository 하나를 부르는 조회라 UseCase 없이 직접 부른다(5.1.2 설계 규칙).

data class GrowthUiState(
    val period: AggregationPeriod = AggregationPeriod.WEEKLY,
    val isLoading: Boolean = true,
    val growth: Growth? = null,
    val error: UiText? = null
)
