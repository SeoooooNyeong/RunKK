package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.RaceStatus
import java.time.LocalDate

/**
 * 주간 목표 정하기(FR-138, 피그마 F-07) 상태. [weekStart]는 이번 주 월요일, [draft]는 목표 거리(km) 입력, [pastWeeks]는 지난 3주
 * 레이스 결과(최신 먼저, 합산 거리·그 주 목표·달성 여부·참여 인원, 읽지 못한 주는 빠진다, [임시:D-성-32])다. 목표는 1km 이상이다.
 */
data class CrewGoalSetUiState(
    val crewId: Long = 0,
    val weekStart: LocalDate = LocalDate.MIN,
    val draft: String = "",
    val pastWeeks: List<RaceStatus> = emptyList(),
    val isSaving: Boolean = false,
    /** 정했다: 화면을 닫는다. */
    val isSaved: Boolean = false,
    val error: UiText? = null
) {
    val draftKm: Double? get() = draft.toDoubleOrNull()

    val canSave: Boolean get() = (draftKm ?: 0.0) >= MIN_KM && !isSaving

    companion object {
        const val MIN_KM = 1.0

        /** 지난 목표 카드 수(피그마 F-07 지난 3주). */
        const val PAST_WEEKS = 3
    }
}
