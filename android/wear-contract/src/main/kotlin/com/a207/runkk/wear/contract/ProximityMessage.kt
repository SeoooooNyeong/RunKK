package com.a207.runkk.wear.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 폰 → 워치 근접 알림(FR-196, D-96). 경로는 WearPaths.PROXIMITY(MessageClient, 유실 허용).
 * 간격은 폰(domain.run ProximityAlertRule)이 격차로 계산해 보내고 워치는 그대로 재생만 한다.
 * gapMeters는 절댓값이고 isAhead가 앞섬 여부다. isActive가 거짓이면 워치는 반복을 멈춘다(이때 gapMeters·intervalMillis는 0).
 * 워치는 마지막 메시지 뒤 3초가 되면(정각 포함, D-158) 스스로 멈추므로 폰은 켜져 있는 동안 2초마다 다시 보낸다.
 */
@Serializable
data class ProximityMessage(
    @SerialName("active") val isActive: Boolean,
    val gapMeters: Int,
    val intervalMillis: Long,
    @SerialName("ahead") val isAhead: Boolean,
    val sentAtEpochMillis: Long
) {
    fun encode(): ByteArray = wearJson.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): ProximityMessage =
            wearJson.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}
