package com.a207.runkk.wear.contract

import kotlinx.serialization.Serializable

/**
 * 워치 → 폰 종료 요청(워치에서 종료 버튼을 길게 누름, FR-181). 폰의 RunTracker가 화면의 길게 누르기 종료와 같은 규칙으로
 * 처리한다(잠금 중이면 거부, 자유런만 받음). 경로는 WearPaths.END_REQUEST(MessageClient).
 */
@Serializable
data class EndRequestMessage(val requestedAtEpochMillis: Long) {
    fun encode(): ByteArray = wearJson.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): EndRequestMessage =
            wearJson.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}
