package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError

/** 서버 파기 지시(GET /me/disposals). 동의 철회 7일 뒤 기기 사본도 지운다(FR-026, FR-029). */
data class DisposalOrder(val orderId: Long, val kind: Kind) {
    enum class Kind {
        ROUTE,
        HEART_RATE
    }
}
