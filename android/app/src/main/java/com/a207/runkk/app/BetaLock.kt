package com.a207.runkk.app

import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.Routes

/**
 * 1차 배포(LOCK_UNRELEASED 참인 빌드)에서 경로로 막는 기능(D-251, FR-223): 크루(하위 경로·인자 키·채팅·알림 이동 크루 화면 포함)와
 * 아바타(만들기·상점·배경 고르기 포함). 보통의 진입점은 화면이 ReleaseScope로 숨기고(D-252), 이 목록은 알림·딥링크로 들어오는
 * 우회 진입을 막는 방어선이다. [RunkkNavHost]가 잠긴 경로 대신 LockedFeatureDialog를 띄우고 뒤로 돌린다(D-253).
 * 셀럽런은 1차 배포 대상이라 목록에서 뺐다. release 빌드는 R8이 클래스 이름을 바꾸므로 이름이 아니라 타입으로 고른다.
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

    private fun isLockedRoute(route: String): Boolean = route == Routes.CREW ||
        route.startsWith("${Routes.CREW}/") ||
        route == Routes.AVATAR ||
        route.startsWith("${Routes.AVATAR}/")
}
