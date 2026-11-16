package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError

/** 서버 전송 확인(POST /runs). isAlreadySaved면 같은 기록ID가 이미 저장돼 있었다(FR-040, D-11). */
data class UploadReceipt(
    val recordId: String,
    val isAlreadySaved: Boolean,
    /** 유효 러닝인가(서버 valid). 거짓이면 받았지만 유효 기여는 0이다(설계 05-32 4.4). 옛 서버면 참이다. */
    val isValid: Boolean = true
)
