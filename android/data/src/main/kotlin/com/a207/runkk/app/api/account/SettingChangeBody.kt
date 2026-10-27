package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/** PATCH /me/settings 요청(서버 SettingChangeRequest: 설정항목 이름·값, ALERT일 때만 알림 종류). */
@Serializable
internal data class SettingChangeBody(val item: String, val value: String, val notificationType: String? = null)
