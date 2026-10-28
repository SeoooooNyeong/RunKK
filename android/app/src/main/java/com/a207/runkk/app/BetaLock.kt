package com.a207.runkk.app

import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.Routes

/**
 * v0.9 베타(Play, prod 플레이버)에서 열지 않는 기능(S15P31A207-218): 크루(채팅 포함), 아바타(상점·배경 포함), 셀럽런.
 * 기능 코드는 그대로 두고 [RunkkNavHost]가 이 경로들을 "추후 추가 예정" 화면으로 바꾼다. 하단 탭·홈·마이·알림함 진입이 모두
 * 화면 그래프를 지나므로 여기 한 곳에서 막힌다. 켜고 끄는 값은 app/build.gradle.kts의 LOCK_UNRELEASED다.
 * release 빌드는 R8이 클래스 이름을 바꾸므로 이름이 아니라 타입으로 고른다.
 */
internal object BetaLock {
    fun isLocked(key: NavKey): Boolean = when (key) {
        is Routes.Key -> isLockedRoute(key.route)

        is Routes.CrewHome,
        is Routes.CrewManage,
        is Routes.CrewRunWrite,
        is Routes.CrewRunLobby,
        is Routes.CrewRunResult,
        is Routes.CrewPollWrite,
        is Routes.CrewBoardWrite,
        is Routes.CrewNoticeWrite,
        is Routes.CrewPoll,
        is Routes.CrewMeetupDetail,
        is Routes.CrewGoalSet,
        is Routes.CrewChat -> true

        else -> false
    }

    private fun isLockedRoute(route: String): Boolean = route == Routes.CELEB ||
        route == Routes.CREW ||
        route.startsWith("${Routes.CREW}/") ||
        route == Routes.AVATAR ||
        route.startsWith("${Routes.AVATAR}/")
}
