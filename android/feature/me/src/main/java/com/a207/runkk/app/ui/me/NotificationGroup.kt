package com.a207.runkk.app.ui.me

import androidx.annotation.StringRes

/**
 * 알림 설정 스위치 하나(F5). 서버 알림 종류 여러 개([types])를 함께 켜고 끈다. [consentItem]이 있으면 알림 종류가 아니라 그 동의 값이다.
 * [descriptionRes]는 묶인 알림을 나열한 보조 줄(없으면 null).
 */
data class NotificationGroup(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int?,
    val types: List<String>,
    val consentItem: String? = null
)
