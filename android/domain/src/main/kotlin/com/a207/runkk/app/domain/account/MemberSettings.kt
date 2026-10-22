package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Distance

/**
 * 회원 설정 화면 값(GET /me/settings: 서버 MemberSettingsView와 동의 최근 값). 필드 이름·형식은 10/21 API 계약으로 확정하고
 * api.account 매퍼가 맞춘다. values의 키·값은 changeSetting(item, value)로 보내는 것과 같은 문자열이고(서버 SettingItem 이름,
 * 러닝·대결 설정은 "RUN_SETTINGS.voice"처럼 하위 항목, 알림은 "ALERT.알림종류"), consents의 키는 서버 ConsentItem 이름이다.
 */
data class MemberSettings(val values: Map<String, String>, val consents: Map<String, Boolean>) {
    /**
     * 대결 설정의 기본 대결 거리(FR-109): 마지막으로 매칭을 요청한 랜덤 대결 거리(FR-080, 서버 StandardDistance 이름 "KM5").
     * 값이 없거나 랜덤 대결 거리(1·3·5·10km)가 아니면 null이다(화면이 기본값 5km를 쓴다).
     */
    val battleDefaultDistance: Distance?
        get() = values[BATTLE_DEFAULT_DISTANCE]?.let(RANDOM_BATTLE_DISTANCES::get)

    companion object {
        /** 대결 설정의 기본 대결 거리 항목 키. */
        const val BATTLE_DEFAULT_DISTANCE = "BATTLE_SETTINGS.defaultDistance"

        /** 랜덤 대결 거리(서버 StandardDistance 중 RANDOM_BATTLE, FR-080). */
        private val RANDOM_BATTLE_DISTANCES: Map<String, Distance> =
            mapOf(
                "KM1" to Distance(1_000),
                "KM3" to Distance(3_000),
                "KM5" to Distance(5_000),
                "KM10" to Distance(10_000)
            )
    }
}
