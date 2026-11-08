package com.a207.runkk.wear.contract

import kotlinx.serialization.Serializable

/**
 * 워치 → 폰 심박 묶음(FR-043). 폰이 측정일시로 진행 중 러닝 또는 끝난 러닝(지연 심박)에 넣는다.
 * 실패 측정은 status FAILED로 표시하고 폰(data.watch)이 거른다.
 * [steps]는 같은 운동 세션이 잰 손목 걸음이다(선택, S15P31A207-242). 옛 폰은 모르는 항목이라 버리고, 옛 워치가 보낸 묶음은 빈 목록으로
 * 읽는다. 걸음만 있고 심박이 없는 묶음([samples]가 빈 목록)도 있다.
 */
@Serializable
data class HeartRateBatchMessage(val samples: List<Sample>, val steps: List<Step> = emptyList()) {
    @Serializable
    data class Sample(val measuredAtEpochMillis: Long, val bpm: Int, val status: Status)

    /**
     * 워치 걸음 한 건. Health Services STEPS_PER_MINUTE이면 [perMinute], STEPS만 지원하는 워치면 그 운동 세션의 누적 걸음 수 [total]이다.
     * 누적 값의 케이던스 계산은 폰(domain CadenceMeter)이 한다.
     */
    @Serializable
    data class Step(val measuredAtEpochMillis: Long, val perMinute: Long? = null, val total: Long? = null)

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
