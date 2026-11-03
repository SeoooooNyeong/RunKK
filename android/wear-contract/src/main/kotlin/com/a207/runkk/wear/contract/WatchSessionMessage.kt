package com.a207.runkk.wear.contract

import kotlinx.serialization.Serializable

/**
 * 폰 → 워치 로그인 정보(FR-240, D-371, D-372). 경로는 WearPaths.WATCH_SESSION이고 **MessageClient로만** 보낸다: DataClient 항목은
 * Data Layer 저장소에 남으므로 비밀값을 싣지 않는다(NFR-050). 토큰 쌍은 서버 POST /auth/watch-session이 워치용으로 따로 준 것이다.
 * [accountType]은 서버 AccountType 상수 이름(SOCIAL·GUEST)이다(wear.contract는 domain을 모른다).
 * [issuedAtEpochMillis]보다 이른 로그아웃 표시(SessionRevokedMessage.revokedAtEpochMillis)는 워치가 버린다(D-390, H-55).
 */
@Serializable
data class WatchSessionMessage(
    val memberId: Long,
    val accountType: String,
    val accessToken: String,
    val refreshToken: String,
    val issuedAtEpochMillis: Long
) {
    fun encode(): ByteArray = wearJson.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    /** 로그에 토큰이 남지 않게 가린다(NFR-050). */
    override fun toString(): String = "WatchSessionMessage(memberId=$memberId, accountType=$accountType, tokens=***, " +
        "issuedAtEpochMillis=$issuedAtEpochMillis)"

    companion object {
        fun decode(bytes: ByteArray): WatchSessionMessage =
            wearJson.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}
