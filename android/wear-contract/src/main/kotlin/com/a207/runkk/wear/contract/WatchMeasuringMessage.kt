package com.a207.runkk.wear.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 워치 → 폰 워치 측정 중 여부(FR-252, D-371, D-373). 경로는 WearPaths.WATCH_MEASURING(MessageClient). 워치는 측정 시작·끝마다,
 * 측정 중에는 30초(제안)마다 다시 보낸다. 폰은 마지막 참이 90초(제안)보다 오래되면 거짓으로 본다(D-390, H-57).
 * Boolean은 Kotlin 이름만 is…이고 JSON 키는 접두어 없는 measuring이다(D-97).
 */
@Serializable
data class WatchMeasuringMessage(@SerialName("measuring") val isMeasuring: Boolean, val atEpochMillis: Long) {
    fun encode(): ByteArray = wearJson.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): WatchMeasuringMessage =
            wearJson.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}
