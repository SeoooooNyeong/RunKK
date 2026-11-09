package com.a207.runkk.app

import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTab
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.navigation.Routes

/**
 * 미공개 기능의 경로 잠금(D-251, FR-223): 경로마다 딸린 기능을 정하고, 지금 배포 범위([ReleaseScope])에서 그 기능이 닫혀 있으면 막는다.
 * 크루(하위 경로·인자 키·채팅·알림 이동 크루 화면 포함)는 [UnreleasedFeature.CREW], 아바타(만들기·상점·배경 고르기 포함)는
 * [UnreleasedFeature.AVATAR_CUSTOMIZE], 런바디(Routes.REPORT)는 [UnreleasedFeature.RUNBODY]다. 2차 배포(S15P31A207-248)는 셋 다
 * 열려 있어 막는 경로가 없다. 보통의 진입점은 화면이 ReleaseScope로 숨기고(D-252), 이 판정은 알림·딥링크로 들어오는 우회 진입을 막는
 * 방어선이다. [RunkkNavHost]가 잠긴 경로 대신 LockedFeatureDialog를 띄우고 뒤로 돌린다(D-253).
 * release 빌드는 R8이 클래스 이름을 바꾸므로 이름이 아니라 타입으로 고른다.
 */
internal object BetaLock {
    /** [key] 경로가 딸린 기능. 딸린 기능이 없으면 null(늘 열림). */
    fun featureOf(key: NavKey): UnreleasedFeature? = when (key) {
        is Routes.Key -> featureOfRoute(key.route)

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
        is Routes.CrewChat -> UnreleasedFeature.CREW

        else -> null
    }

    /** [scope]에서 [key] 경로를 막는가: 딸린 기능이 닫혀 있을 때만. */
    fun isLocked(key: NavKey, scope: ReleaseScope): Boolean = featureOf(key)?.let { !scope.isOpen(it) } ?: false

    /** [scope]에서 자물쇠를 다는 하단 탭: 크루가 닫혀 있으면 크루 탭(D-253). */
    fun lockedTabs(scope: ReleaseScope): Set<RunkkTab> =
        if (scope.isOpen(UnreleasedFeature.CREW)) emptySet() else setOf(RunkkTab.CREW)

    private fun featureOfRoute(route: String): UnreleasedFeature? = when {
        route == Routes.CREW || route.startsWith("${Routes.CREW}/") -> UnreleasedFeature.CREW

        route == Routes.AVATAR || route.startsWith("${Routes.AVATAR}/") -> UnreleasedFeature.AVATAR_CUSTOMIZE

        route == Routes.REPORT || route == Routes.COACH || route == Routes.COACH_CONSENTS ->
            UnreleasedFeature.RUNBODY

        else -> null
    }
}
