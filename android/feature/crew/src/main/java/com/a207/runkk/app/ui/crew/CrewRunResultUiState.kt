package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.account.MyAvatar
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.ui.share.RecordResultDetail

/** 크루런 결과(F-16)와 내 기록 상세(F-16b, [detail]은 내 기록이 올라가 읽은 뒤에 있다). */
data class CrewRunResultUiState(
    val isLoading: Boolean = true,
    val result: CrewRunResult? = null,
    val detail: RecordResultDetail? = null,
    val error: UiText? = null,
    /** 가운데 내 아바타의 얼굴·배경(홈과 같은 출처). 읽기 전·실패면 null이고 기본 아바타를 그린다. */
    val myAvatar: MyAvatar? = null
)
