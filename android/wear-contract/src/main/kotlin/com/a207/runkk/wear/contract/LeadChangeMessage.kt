package com.a207.runkk.wear.contract

import kotlinx.serialization.Serializable

/**
 * 폰 → 워치 추월·역전 순간(FR-188, FR-196). 경로는 WearPaths.LEAD_CHANGE(MessageClient).
 * 워치는 1초 긴 진동을 한 번 낸다. 근접 알림 반복 중이어도 이 진동을 먼저 낸다.
 * 폰은 근접 알림이 켜져 있을 때만 보낸다(D-145).
 */
@Serializable
data class LeadChangeMessage(val kind: Kind, val atEpochMillis: Long) {
    @Serializable
    enum class Kind {
        /** 내가 앞질렀다. */
        OVERTOOK,

        /** 상대가 앞질렀다. */
        OVERTAKEN
    }

    fun encode(): ByteArray = wearJson.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): LeadChangeMessage =
            wearJson.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}
