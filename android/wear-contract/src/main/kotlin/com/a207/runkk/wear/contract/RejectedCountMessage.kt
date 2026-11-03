package com.a207.runkk.wear.contract

import kotlinx.serialization.Serializable

/**
 * 워치 → 폰 워치의 거부 기록(REJECTED) 건수(W43). 경로는 WearPaths.REJECTED_COUNT(DataClient 항목). 워치는 건수가 바뀔 때마다 넣고,
 * 폰은 설정의 "못 올린 기록 n건"에 폰 건수와 더해 보인다. [atEpochMillis]는 워치가 센 시각(워치 시계)이고, 폰은 늦게 도착한 옛 건수를
 * 이 값으로 가려 버린다. 이 경로를 모르는 옛 폰은 버린다(D-97, F.4.4).
 */
@Serializable
data class RejectedCountMessage(val count: Int, val atEpochMillis: Long) {
    fun encode(): ByteArray = wearJson.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): RejectedCountMessage =
            wearJson.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}
