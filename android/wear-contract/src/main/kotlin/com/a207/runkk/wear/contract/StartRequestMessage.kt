package com.a207.runkk.wear.contract

import kotlinx.serialization.Serializable

/** 워치 → 폰 시작 요청(자유런만, FR-183). 폰의 RunTracker가 받을지 정한다. */
@Serializable
data class StartRequestMessage(val requestedAtEpochMillis: Long) {
    fun encode(): ByteArray = wearJson.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): StartRequestMessage =
            wearJson.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}
