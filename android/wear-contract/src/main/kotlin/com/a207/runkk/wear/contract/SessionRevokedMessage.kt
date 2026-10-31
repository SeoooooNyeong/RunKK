package com.a207.runkk.wear.contract

import kotlinx.serialization.Serializable

/**
 * 폰 → 워치 로그아웃 표시(FR-241, D-371). 경로는 WearPaths.SESSION_REVOKED이고 **DataClient 항목**이다: 연결이 끊긴 동안에도 남았다가
 * 다음 연결 때 전달된다. 비밀값은 없다. 워치는 [revokedAtEpochMillis]가 받아 둔 로그인 정보의 issuedAtEpochMillis보다 이르면 버리고,
 * 폰은 새 로그인 정보를 보낸 뒤 옛 항목을 지운다(D-390, H-55).
 */
@Serializable
data class SessionRevokedMessage(val memberId: Long, val revokedAtEpochMillis: Long) {
    fun encode(): ByteArray = wearJson.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): SessionRevokedMessage =
            wearJson.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}
