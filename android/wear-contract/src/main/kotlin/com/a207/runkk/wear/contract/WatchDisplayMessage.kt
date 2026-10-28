package com.a207.runkk.wear.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 폰 → 워치 표시 정보(FR-042, N-21). isRunning이 거짓이면 워치 시작 화면용(오늘 계획)이다.
 * isHeartRateCollectionAllowed는 폰의 심박 동의·워치 연동 값이고, 거짓이면 워치가 심박을 모으지 않는다(NFR-023).
 * notice는 폰이 문자열 리소스로 바꿔 보낸 안내다(시작 요청을 버렸을 때, 정지 자동 종료 예고·종료 등, D-111).
 * isPausable은 자유런 측정 중일 때만 참이고(FR-194), 참일 때만 워치가 일시정지 요청을 보낸다. isPaused면 워치는 "일시정지"와 재개 버튼을 보인다.
 *
 * 하위 호환(D-97, F.4.4): Boolean은 Kotlin 이름만 is…이고 JSON 키는 @SerialName으로 접두어 없는 이름을 지킨다.
 * heartRateCollectionAllowed가 없는 메시지(옛 폰 앱)는 개인정보 쪽으로 안전한 값(수집 금지)으로 읽고(S-20, D-53),
 * paused·pausable이 없는 메시지(v0.10 폰 앱)는 거짓으로 읽어 워치가 일시정지 버튼을 숨긴다.
 */
@Serializable
data class WatchDisplayMessage(
    @SerialName("running") val isRunning: Boolean,
    val elapsedSeconds: Long,
    val distanceMeters: Long,
    val currentPaceSecondsPerKm: Int? = null,
    val heartRateBpm: Int? = null,
    val planDistanceMeters: Long? = null,
    val planPaceSecondsPerKm: Int? = null,
    @SerialName("heartRateCollectionAllowed") val isHeartRateCollectionAllowed: Boolean = false,
    val notice: String? = null,
    @SerialName("paused") val isPaused: Boolean = false,
    @SerialName("pausable") val isPausable: Boolean = false
) {
    fun encode(): ByteArray = wearJson.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): WatchDisplayMessage =
            wearJson.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}
