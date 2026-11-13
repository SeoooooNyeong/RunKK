package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.PastGoals
import java.time.LocalDate

/** 지난 목표 화면 상태(피그마 F-21). [nextBefore]가 null이면 더 읽을 쪽이 없다. */
data class CrewPastGoalsUiState(
    val crewId: Long = 0,
    val goals: List<PastGoals.Goal> = emptyList(),
    val nextBefore: LocalDate? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** 첫 쪽을 한 번이라도 읽었는가(빈 목록 안내를 보일지). */
    val isLoaded: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null
)
