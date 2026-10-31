package com.a207.runkk.app.core.designsystem

/** 알림 권한(POST_NOTIFICATIONS)이 생긴 API 수준(Android 13). */
const val NOTIFICATION_PERMISSION_API = 33

/**
 * 진행 알림 권한을 물을 때인가(D-259 기본값, TC-9620): Android 13 이상이고, 권한이 없고, 이 화면에서 아직 묻지 않았을 때다.
 * 자유런 카운트다운과 대결 진행 화면(배틀런·친구 대결·셀프런·셀럽런)이 같은 규칙으로 묻는다(AN8-5). 거부해도 측정은 한다.
 */
fun shouldAskNotificationPermission(sdkInt: Int, isGranted: Boolean, isAsked: Boolean): Boolean =
    sdkInt >= NOTIFICATION_PERMISSION_API && !isGranted && !isAsked
