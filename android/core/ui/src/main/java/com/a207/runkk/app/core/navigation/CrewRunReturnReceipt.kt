package com.a207.runkk.app.core.navigation

import java.time.Instant

/**
 * 크루런 결과 「확인」의 복귀 값(설계 05-32 CrewRunReturnReceipt). 내 기록이 서버에 저장된 뒤에만 만든다. [receiptId]는 내 기록 ID
 * 기준이라 같은 기록을 두 번 강조하지 않는다. [ownDistanceMeters]는 내 유효 거리(유효하지 않은 기록이면 0)다. 타인 거리·전체 정산
 * 값은 없다(U-34).
 */
data class CrewRunReturnReceipt(
    val receiptId: String,
    val postId: Long,
    val myRecordId: String,
    val ownDistanceMeters: Long,
    val uploadedAt: Instant,
    val crewId: Long? = null
) {
    /** 홈에 넘길 값(크루 ID를 뺀 불변 투영). */
    fun forHome(): HomeRunReturnReceipt =
        HomeRunReturnReceipt(receiptId, postId, myRecordId, ownDistanceMeters, uploadedAt)
}
