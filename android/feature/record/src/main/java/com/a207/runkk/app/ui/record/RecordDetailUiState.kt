package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.record.RecordDetail

/** 기록 상세(G-9). [isConfirmingDelete]는 삭제 확인 대화상자, [isDeleting]은 삭제 요청 중, [message]는 한 번 보일 문구. */
data class RecordDetailUiState(
    val isLoading: Boolean = true,
    val recordId: String? = null,
    val detail: RecordDetail? = null,
    val error: UiText? = null,
    val isConfirmingDelete: Boolean = false,
    val isDeleting: Boolean = false,
    val message: UiText? = null
)
