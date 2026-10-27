package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.RaceStatus
import java.time.LocalDate

/**
 * 주간 공동 목표 정하기(FR-138, 와이어프레임 M8) 상태. [weekStart]는 이번 주 월요일, [draft]는 목표 거리(km) 입력, [lastWeek]은 지난 주
 * 레이스 결과(합산 거리, 그 주 목표, 달성 여부, 참여 인원)다. 목표는 1km 이상이다(GOAL_INVALID).
 */
data class CrewGoalSetUiState(
    val crewId: Long = 0,
    val weekStart: LocalDate = LocalDate.MIN,
    val draft: String = "",
    val lastWeek: RaceStatus? = null,
    val isSaving: Boolean = false,
    /** 정했다: 화면을 닫는다. */
    val isSaved: Boolean = false,
    val error: UiText? = null
) {
    val draftKm: Double? get() = draft.toDoubleOrNull()

    val canSave: Boolean get() = (draftKm ?: 0.0) >= MIN_KM && !isSaving

    companion object {
        const val MIN_KM = 1.0

        /** 빠른 선택(km). */
        val QUICK_KM: List<Int> = listOf(50, 80, 100)
    }
}
