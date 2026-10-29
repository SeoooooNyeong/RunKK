package com.a207.runkk.app.ui.notification

import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.Routes

/**
 * 알림 이동 표(FR-175, FR-178, D-150): 서버 notification.Screen 11개 → core.navigation 경로. 대상 ID(크루·대결 ID)가 있으면
 * 그 화면을, 없으면 그 기능의 첫 화면을 연다. 모르는 화면 값(옛 앱이 모르는 새 값, 지운 CREW_BOARD)은 null이고 알림함에 머문다.
 * 크루 공지·투표·일정 알림의 대상 ID는 글 ID라(서버 CrewNewsNotifier) 크루 홈 경로에 쓰지 않고 내 크루 홈의 해당 탭 경로
 * (Routes.CREW_NOTICE·CREW_POLL·CREW_SCHEDULE, ui.crew가 GET /crews/me로 내 크루를 찾는다)로 연다. 채팅 알림의 대상 ID는 크루 ID라
 * 그 크루의 채팅방(Routes.CrewChat)을, 없으면 내 크루를 확인하는 채팅 경로(Routes.CREW_CHAT)를 연다.
 * 신고 처리 결과(REPORT_RESOLVED)는 서버가 CREW_HOME으로 보낸다(D-149).
 */
internal fun inboxDestination(screen: String?, targetId: String?): NavKey? {
    val id = targetId?.toLongOrNull()
    return when (screen) {
        "COACH_PROGRESS" -> Routes.Key(Routes.COACH)
        "AVATAR" -> Routes.Key(Routes.AVATAR)
        "AVATAR_BACKGROUND" -> Routes.Key(Routes.AVATAR_BACKGROUND)
        "CREW_MANAGE" -> id?.let(Routes::CrewManage) ?: Routes.Key(Routes.CREW)
        "CREW_HOME" -> id?.let(Routes::CrewHome) ?: Routes.Key(Routes.CREW)
        "CREW_NOTICE" -> Routes.Key(Routes.CREW_NOTICE)
        "CREW_POLL" -> Routes.Key(Routes.CREW_POLL)
        "CREW_SCHEDULE" -> Routes.Key(Routes.CREW_SCHEDULE)
        "CREW_CHAT" -> id?.let(Routes::CrewChat) ?: Routes.Key(Routes.CREW_CHAT)
        "CREW_RUN_RECRUIT" -> Routes.Key(Routes.CREW)
        "BATTLE_RESULT" -> id?.let(Routes::BattleResult) ?: Routes.Key(Routes.RECORDS_BATTLES)
        else -> null
    }
}

/** 이동 표가 다루는 서버 화면 이름(계약 시험이 서버 Screen과 맞춘다). */
internal val INBOX_SCREENS: Set<String> = setOf(
    "COACH_PROGRESS",
    "AVATAR",
    "AVATAR_BACKGROUND",
    "CREW_MANAGE",
    "CREW_HOME",
    "CREW_NOTICE",
    "CREW_POLL",
    "CREW_SCHEDULE",
    "CREW_CHAT",
    "CREW_RUN_RECRUIT",
    "BATTLE_RESULT"
)
