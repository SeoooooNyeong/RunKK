package com.a207.runkk.app.ui.me

import com.a207.runkk.app.domain.account.ChangeSettingUseCase
import com.a207.runkk.app.domain.account.MemberSettings

/**
 * 설정 항목 키(서버 SettingItem 이름, 러닝·대결 설정은 하위 항목, 알림은 "ALERT.알림종류"). MemberSettings.values의 키이자
 * ChangeSettingUseCase(Change(item, value))의 item이다. 10/21 API 계약에서 이름이 다르면 api.account 매퍼가 맞춘다.
 */
object SettingKeys {
    const val UNIT = "UNIT"
    const val RECORD_VISIBILITY = "RECORD_VISIBILITY"
    const val COURSE_MAP_SHARED = "COURSE_MAP_SHARED"
    const val PROFILE_VISIBILITY = "PROFILE_VISIBILITY"
    const val WATCH_LINKED = "WATCH_LINK"
    const val RUN_TARGET = "RUN_SETTINGS.target"
    const val RUN_VOICE = "RUN_SETTINGS.voice"
    const val RUN_VIBRATION = "RUN_SETTINGS.vibration"
    const val RUN_FOLLOW_COACH_PLAN = "RUN_SETTINGS.followCoachPlan"
    const val BATTLE_VOICE = "BATTLE_SETTINGS.voice"
    const val BATTLE_VIBRATION = "BATTLE_SETTINGS.vibration"
    const val BATTLE_GAP_ALERT = "BATTLE_SETTINGS.gapAlertInterval"

    /** 대결 근접 알림(워치 소리·진동, FR-109·FR-196, 서버 BattleSettings.proximityAlert, 기본 켬). */
    const val BATTLE_PROXIMITY_ALERT = "BATTLE_SETTINGS.proximityAlert"

    /** 화면 테마(FR-203, 서버 SettingItem.THEME, 기본 SYSTEM). */
    const val THEME = "THEME"

    /** 알림 종류(자료사전 알림종류 12개) 수신 여부 키. */
    fun alert(type: String): String = "ALERT.$type"

    /** 서버에 사본이 없을 때 쓰는 기본값(srs 기본값: 단위 km, 전체공개, 코스 지도 꺼짐, 개인정보 비공개, 음성·진동 켬, 알림 켬). */
    val DEFAULTS: Map<String, String> = mapOf(
        UNIT to "KM",
        RECORD_VISIBILITY to "PUBLIC",
        COURSE_MAP_SHARED to "false",
        PROFILE_VISIBILITY to "PRIVATE",
        WATCH_LINKED to "false",
        RUN_TARGET to "FREE",
        RUN_VOICE to "true",
        RUN_VIBRATION to "true",
        RUN_FOLLOW_COACH_PLAN to "false",
        BATTLE_VOICE to "true",
        BATTLE_VIBRATION to "true",
        BATTLE_GAP_ALERT to "KM1",
        BATTLE_PROXIMITY_ALERT to "true",
        THEME to "SYSTEM"
    )
}
