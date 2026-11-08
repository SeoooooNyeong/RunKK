package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import java.time.LocalDate
import java.time.LocalTime

/**
 * 크루런 모집하기(FR-132, 피그마 F-12) 상태: 제목·내용·거리·모집 일시. 대면 모임은 앱에서 만들지 않는다(서버 API는 남아 있다).
 * 작성자는 서버가 참석으로 넣는다(D-151).
 */
data class CrewRunWriteUiState(
    val title: String = "",
    val body: String = "",
    val distance: Distance = Distance(5_000),
    /** 오늘부터 며칠 뒤(0~6). */
    val dayOffset: Int = 1,
    val time: LocalTime = LocalTime.of(DEFAULT_HOUR, 0),
    val today: LocalDate = LocalDate.MIN,
    val isSaving: Boolean = false,
    val error: UiText? = null
) {
    val isValid: Boolean
        get() = title.isNotBlank()

    companion object {
        const val DEFAULT_HOUR = 7
        const val DAYS = 7
        val TIMES: List<LocalTime> = (5..23).flatMap { h -> listOf(LocalTime.of(h, 0), LocalTime.of(h, 30)) }
    }
}
