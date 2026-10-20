package com.a207.runkk.wear.contract

import kotlinx.serialization.Serializable

/**
 * 폰 → 워치 표시 정보(FR-042, N-21). running이 거짓이면 워치 시작 화면용(오늘 계획)이다.
 * heartRateCollectionAllowed는 폰의 심박 동의·워치 연동 값이고, 거짓이면 워치가 심박을 모으지 않는다(NFR-023).
 * notice는 시작 요청을 버렸을 때의 안내("폰에서 러닝 화면을 여세요" 등)다.
 */
@Serializable
data class WatchDisplayMessage(
    val running: Boolean,
    val elapsedSeconds: Long,
    val distanceMeters: Long,
    val currentPaceSecondsPerKm: Int? = null,
    val heartRateBpm: Int? = null,
    val planDistanceMeters: Long? = null,
    val planPaceSecondsPerKm: Int? = null,
    val heartRateCollectionAllowed: Boolean,
    val notice: String? = null
) {
    fun encode(): ByteArray = wearJson.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): WatchDisplayMessage =
            wearJson.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}
