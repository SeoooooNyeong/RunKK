package com.a207.runkk.app.ui.crew

/** 모집하기(크루 ID로 새 글) 또는 수정(모집글 ID, 피그마 F-13d). */
sealed interface CrewRunWriteMode {
    data class Create(val crewId: Long) : CrewRunWriteMode

    data class Edit(val postId: Long) : CrewRunWriteMode
}
