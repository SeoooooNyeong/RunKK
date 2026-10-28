package com.a207.runkk.wear.contract

import kotlinx.serialization.Serializable

/**
 * 워치 → 폰 심박 묶음(FR-043). 폰이 측정일시로 진행 중 러닝 또는 끝난 러닝(지연 심박)에 넣는다.
 * 실패 측정은 status FAILED로 표시하고 폰(data.watch)이 거른다.
 */
@Serializable
data class HeartRateBatchMessage(val samples: List<Sample>) {
    @Serializable
    data class Sample(val measuredAtEpochMillis: Long, val bpm: Int, val status: Status)

    @Serializable
    enum class Status {
        OK,
        FAILED
    }

    fun encode(): ByteArray = wearJson.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): HeartRateBatchMessage =
            wearJson.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}
