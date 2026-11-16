package com.a207.runkk.app.core.navigation

import java.time.Instant

/** 홈 이번 주 목표에 필요한 복귀 값만(설계 05-32 HomeRunReturnReceipt). */
data class HomeRunReturnReceipt(
    val receiptId: String,
    val postId: Long,
    val myRecordId: String,
    val ownDistanceMeters: Long,
    val uploadedAt: Instant
)
