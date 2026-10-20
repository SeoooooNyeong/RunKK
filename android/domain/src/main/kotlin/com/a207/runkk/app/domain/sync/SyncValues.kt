package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError

/** 서버 전송 확인(POST /runs). alreadySaved면 같은 기록ID가 이미 저장돼 있었다(FR-040, D-11). */
data class UploadReceipt(val recordId: String, val alreadySaved: Boolean)

/** 보관 기록 하나의 전송 결과. UploadWorker가 Failed가 있으면 RetryPolicy 간격 뒤 다시 실행한다. */
sealed interface UploadOutcome {
    data class Sent(val recordId: String) : UploadOutcome

    data class AlreadySaved(val recordId: String) : UploadOutcome

    /** recordId가 null이면 보관함 자체를 읽지 못했다. */
    data class Failed(val recordId: String?, val error: AppError) : UploadOutcome
}

/** 서버 파기 지시(GET /me/disposals). 동의 철회 7일 뒤 기기 사본도 지운다(FR-026, FR-029). */
data class DisposalOrder(val orderId: Long, val kind: Kind) {
    enum class Kind {
        ROUTE,
        HEART_RATE
    }
}
