package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.codePointLength

/**
 * 공지 쓰기(FR-205, 와이어프레임 M1) 상태. 본문은 1~1,000자(앞뒤 공백 제외, 코드 포인트 D-143), [isPinned]면 올리면서 크루 홈 위에
 * 고정한다. [pinnedCount]는 지금 고정된 공지 수(읽기 전이면 null)이고 고정은 3개까지다(넘으면 서버가 PIN_LIMIT로 거부한다).
 */
data class CrewNoticeWriteUiState(
    val body: String = "",
    val isPinned: Boolean = true,
    val pinnedCount: Int? = null,
    val isSaving: Boolean = false,
    /** 올렸다: 화면을 닫는다. */
    val isPosted: Boolean = false,
    val error: UiText? = null
) {
    val canSubmit: Boolean get() = body.isNotBlank() && body.trim().codePointLength() <= BODY_MAX && !isSaving

    companion object {
        const val BODY_MAX = 1_000
        const val PIN_MAX = 3
    }
}
