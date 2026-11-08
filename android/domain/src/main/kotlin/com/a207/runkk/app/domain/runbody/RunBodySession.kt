package com.a207.runkk.app.domain.runbody

/**
 * 기록 하나의 분석 상태(서버 RunBodySessionView). [result]는 [status]가 DONE일 때만 있다. [failureReason]은 FAILED일 때 서버가 남긴
 * 거부 사유다(예: [RunBodyRepository.BIRTH_YEAR_MISSING]).
 */
data class RunBodySession(
    val recordId: String,
    val status: RunBodyStatus,
    val result: RunBodyResult?,
    val failureReason: String? = null
)
