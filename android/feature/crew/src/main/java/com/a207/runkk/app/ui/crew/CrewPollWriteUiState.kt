package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.codePointLength
import com.a207.runkk.app.domain.crew.PollMethod
import java.time.LocalDate
import java.time.LocalTime

/**
 * 투표 만들기(FR-206, D-155, D-165) 상태. 질문 1~200자, 선택지 방식이면 선택지 2~5개·각 1~40자, 찬반은 선택지를 보내지 않는다.
 * 마감은 오늘부터 [dayOffset]일 뒤 [time]이고 지금보다 뒤여야 한다.
 */
data class CrewPollWriteUiState(
    val question: String = "",
    val method: PollMethod = PollMethod.CHOICES,
    val options: List<String> = listOf("", ""),
    val dayOffset: Int = 1,
    val time: LocalTime = LocalTime.of(DEFAULT_HOUR, 0),
    val today: LocalDate = LocalDate.MIN,
    val isSaving: Boolean = false,
    /** 올렸다: 화면을 닫는다. */
    val isPosted: Boolean = false,
    val error: UiText? = null
) {
    val isValid: Boolean
        get() = question.isNotBlank() &&
            question.trim().codePointLength() <= QUESTION_MAX &&
            (
                method == PollMethod.YES_NO || options.all {
                    it.isNotBlank() && it.trim().codePointLength() <= OPTION_MAX
                }
                )

    val canAddOption: Boolean get() = method == PollMethod.CHOICES && options.size < OPTIONS_MAX

    val canRemoveOption: Boolean get() = options.size > OPTIONS_MIN

    companion object {
        const val QUESTION_MAX = 200
        const val OPTION_MAX = 40
        const val OPTIONS_MIN = 2
        const val OPTIONS_MAX = 5
        const val DEFAULT_HOUR = 21
        const val DAYS = 7
    }
}
