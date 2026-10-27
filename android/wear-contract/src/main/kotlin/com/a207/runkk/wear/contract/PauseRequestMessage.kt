package com.a207.runkk.wear.contract

import kotlinx.serialization.Serializable

/**
 * 워치 → 폰 일시정지·재개 요청(FR-194). 경로는 WearPaths.PAUSE_REQUEST(MessageClient).
 * 워치는 마지막으로 받은 표시의 isPausable이 참일 때만 보낸다. 폰의 RunTracker가 폰 버튼과 같은 규칙으로 처리하고,
 * 자유런이 아니거나 상태가 맞지 않으면 무시한 뒤 다음 표시로 상태를 바로잡는다.
 * 정지 구간의 시각은 폰이 요청을 처리한 시각이다. requestedAtEpochMillis는 기록용이다(D-157 (5)).
 * 모르는 action 값은 해독 실패(SerializationException)이고 받는 쪽이 버린다(D-157 (6)).
 */
@Serializable
data class PauseRequestMessage(val action: Action, val requestedAtEpochMillis: Long) {
    @Serializable
    enum class Action {
        PAUSE,
        RESUME
    }

    fun encode(): ByteArray = wearJson.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): PauseRequestMessage =
            wearJson.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}
