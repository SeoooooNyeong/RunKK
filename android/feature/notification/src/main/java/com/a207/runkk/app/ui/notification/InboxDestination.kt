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

        // 크루 관리 화면은 지웠다(W7): 가입 신청 알림은 크루 홈으로 연다(가입 신청은 크루 정보에 있다).
        "CREW_MANAGE" -> id?.let(Routes::CrewHome) ?: Routes.Key(Routes.CREW)

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

/** 바깥 브라우저로 여는 서버 화면 이름(운영자 공지 NOTICE, S15P31A207-275). 대상 ID가 주소다. */
internal const val LINK_SCREEN = "EXTERNAL_LINK"

/**
 * 운영자 공지의 링크: 화면이 [LINK_SCREEN]이고 대상 ID가 공백 없는 https 주소일 때만 그 주소. 아니면 null이고 알림함에 머문다.
 * 서버가 구글 폼 주소만 받지만 앱도 https 밖의 스킴(intent:, http 등)은 열지 않는다.
 */
internal fun inboxLink(screen: String?, targetId: String?): String? =
    targetId?.takeIf { screen == LINK_SCREEN && it.startsWith("https://") && it.none(Char::isWhitespace) }

/** 이동 표가 다루는 서버 화면 이름(계약 시험이 서버 Screen과 맞춘다. [LINK_SCREEN]은 이동 표 밖이다). */
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
