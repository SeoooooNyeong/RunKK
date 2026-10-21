package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Distance

/**
 * 회원 설정 화면 값(GET /me/settings: 서버 MemberSettingsView와 동의 최근 값). 필드 이름·형식은 10/21 API 계약으로 확정하고
 * api.account 매퍼가 맞춘다. values의 키·값은 changeSetting(item, value)로 보내는 것과 같은 문자열이고(서버 SettingItem 이름,
 * 러닝·대결 설정은 "RUN_SETTINGS.voice"처럼 하위 항목, 알림은 "ALERT.알림종류"), consents의 키는 서버 ConsentItem 이름이다.
 */
data class MemberSettings(val values: Map<String, String>, val consents: Map<String, Boolean>)
