package com.a207.runkk.app.ui.notification

/** 알림함 맨 위 이벤트 항목(피그마 62209:1274): 진행 중인 이벤트 수와 안 본 진행 중 이벤트가 있는가(라임 점). */
data class InboxEventSummary(val ongoingCount: Int, val hasUnseen: Boolean)
