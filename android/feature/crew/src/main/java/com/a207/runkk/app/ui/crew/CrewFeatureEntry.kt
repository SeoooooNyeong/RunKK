package com.a207.runkk.app.ui.crew

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import javax.inject.Inject

/**
 * 크루 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14): 크루 탭(찾기), 만들기, 내 크루 홈, 지난 크루런·지난 목표(W7),
 * 일정 쓰기·크루런 대기실/진행·결과, 주간 공동 목표, 알림 이동 경로(크루 홈, 크루런 탭, 채팅방).
 * 게시판 글쓰기·공지 쓰기·투표·대면 모임 상세는 앱에서 뺐다(S15P31A207-236, 서버 API는 남아 있다).
 */
class CrewFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.CREW

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.CREW) { CrewFindRoute() }
        builder.route(Routes.CREW_CREATE) { CrewCreateRoute() }
        builder.route(Routes.CREW_SEARCH) { CrewSearchRoute() }
        builder.addEntryProvider(Routes.CrewHome::class) { key -> CrewHomeRoute(key) }
        builder.addEntryProvider(Routes.CrewPastRuns::class) { key -> CrewPastRunsRoute(key) }
        builder.addEntryProvider(Routes.CrewPastGoals::class) { key -> CrewPastGoalsRoute(key) }
        builder.addEntryProvider(Routes.CrewRunWrite::class) { key ->
            CrewRunWriteRoute(CrewRunWriteMode.Create(key.crewId))
        }
        builder.addEntryProvider(Routes.CrewRunEdit::class) { key ->
            CrewRunWriteRoute(CrewRunWriteMode.Edit(key.postId))
        }
        builder.addEntryProvider(Routes.CrewRunDetail::class) { key -> CrewRunDetailRoute(key) }
        builder.addEntryProvider(Routes.CrewRunLobby::class) { key -> CrewRunRoute(key) }
        builder.addEntryProvider(Routes.CrewRunResult::class) { key -> CrewRunResultRoute(key.postId) }
        builder.addEntryProvider(Routes.CrewGoalSet::class) { key -> CrewGoalSetRoute(key) }
        // 알림 이동(FR-178, D-150): 내 크루 홈의 해당 탭, 채팅은 내 크루를 확인한 뒤 크루 홈 채팅 탭(CrewHome(crewId, "CHAT"))으로.
        // 게시판을 뺀 뒤 공지·투표 알림은 크루 홈 레이스 탭으로 연다.
        builder.route(Routes.CREW_NOTICE) { CrewFindRoute(initialTab = CrewTab.RACE) }
        builder.route(Routes.CREW_POLL) { CrewFindRoute(initialTab = CrewTab.RACE) }
        builder.route(Routes.CREW_SCHEDULE) { CrewFindRoute(initialTab = CrewTab.SCHEDULE) }
        builder.route(Routes.CREW_CHAT) { CrewChatEntryRoute() }
    }
}
