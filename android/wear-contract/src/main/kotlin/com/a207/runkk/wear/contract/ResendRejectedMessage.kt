package com.a207.runkk.wear.contract

import kotlinx.serialization.Serializable

/**
 * 폰 → 워치 거부 기록 다시 보내기 요청(W40). 폰 설정의 "다시 보내기"를 누르면 보낸다. 경로는 WearPaths.RESEND_REJECTED(DataClient 항목,
 * W43: 워치가 연결되지 않았을 때 누른 요청도 연결되면 전달된다). 워치는 서버가 거부해 멈춘 자기 기록(REJECTED)을 미전송으로 되돌리고
 * 전송을 깨운다. 본문은 요청 시각뿐이다. 요청 시각이 매번 달라 항목이 바뀌고, 워치는 이미 처리한 요청 시각을 다시 처리하지 않는다.
 * 이 경로를 모르는 옛 워치는 버린다(D-97, F.4.4).
 */
@Serializable
data class ResendRejectedMessage(val requestedAtEpochMillis: Long) {
    fun encode(): ByteArray = wearJson.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): ResendRejectedMessage =
            wearJson.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}
