package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError

/** 서버 전송 확인(POST /runs). isAlreadySaved면 같은 기록ID가 이미 저장돼 있었다(FR-040, D-11). */
data class UploadReceipt(val recordId: String, val isAlreadySaved: Boolean)
