package com.a207.runkk.app.ui.me

/**
 * 제공자 앱 키(빌드 속성 KAKAO_NATIVE_APP_KEY·GOOGLE_WEB_CLIENT_ID, local.properties → Gradle 속성 → 환경 변수 순).
 * 저장소에 넣지 않으며, 비어 있으면 그 로그인은 "준비 중"을 돌려준다.
 */
data class SocialLoginKeys(val kakaoNativeAppKey: String, val googleWebClientId: String) {
    override fun toString(): String =
        "SocialLoginKeys(kakao=${mask(kakaoNativeAppKey)}, google=${mask(googleWebClientId)})"

    private fun mask(key: String): String = if (key.isBlank()) "(없음)" else "***"
}
