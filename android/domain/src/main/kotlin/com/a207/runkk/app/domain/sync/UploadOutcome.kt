package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError

/** 보관 기록 하나의 전송 결과. UploadWorker가 Failed가 있으면 RetryPolicy 간격 뒤 다시 실행한다. */
sealed interface UploadOutcome {
    data class Sent(val recordId: String) : UploadOutcome

    data class AlreadySaved(val recordId: String) : UploadOutcome

    /** recordId가 null이면 보관함 자체를 읽지 못했다. */
    data class Failed(val recordId: String?, val error: AppError) : UploadOutcome
}
