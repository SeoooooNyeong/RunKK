package com.a207.runkk.app.ui.crew

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import javax.inject.Inject

/**
 * 크루 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14): 크루 탭(찾기), 만들기, 내 크루 홈, 크루 관리,
 * 일정 쓰기(크루런·대면 모임)·크루런 대기실/진행·결과, 투표 만들기, 게시판 글쓰기, 공지 쓰기, 투표, 대면 모임 상세, 주간 공동 목표,
 * 알림 이동 경로(게시판 탭의 공지·투표 칩, 일정 탭, 채팅방).
 */
class CrewFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.CREW

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.CREW) { CrewFindRoute() }
        builder.route(Routes.CREW_CREATE) { CrewCreateRoute() }
        builder.addEntryProvider(Routes.CrewHome::class) { key -> CrewHomeRoute(key) }
        builder.addEntryProvider(Routes.CrewManage::class) { key -> CrewManageRoute(key) }
        builder.addEntryProvider(Routes.CrewRunWrite::class) { key -> CrewRunWriteRoute(key) }
        builder.addEntryProvider(Routes.CrewRunLobby::class) { key -> CrewRunRoute(key) }
        builder.addEntryProvider(Routes.CrewRunResult::class) { key -> CrewRunResultRoute(key.postId) }
        builder.addEntryProvider(Routes.CrewPollWrite::class) { key -> CrewPollWriteRoute(key) }
        builder.addEntryProvider(Routes.CrewBoardWrite::class) { key -> CrewBoardWriteRoute(key) }
        builder.addEntryProvider(Routes.CrewNoticeWrite::class) { key -> CrewNoticeWriteRoute(key) }
        builder.addEntryProvider(Routes.CrewPoll::class) { key -> CrewPollRoute(key) }
        builder.addEntryProvider(Routes.CrewMeetupDetail::class) { key -> CrewMeetupDetailRoute(key) }
        builder.addEntryProvider(Routes.CrewGoalSet::class) { key -> CrewGoalSetRoute(key) }
        // 알림 이동(FR-178, D-150): 내 크루 홈의 해당 탭(공지·투표는 게시판 탭의 그 칩), 채팅은 내 크루를 확인한 뒤
        // Routes.CrewChat(crewId)으로.
        builder.route(Routes.CREW_NOTICE) {
            CrewFindRoute(initialTab = CrewTab.BOARD, initialFilter = BoardFilter.NOTICE)
        }
        builder.route(Routes.CREW_POLL) {
            CrewFindRoute(initialTab = CrewTab.BOARD, initialFilter = BoardFilter.POLL)
        }
        builder.route(Routes.CREW_SCHEDULE) { CrewFindRoute(initialTab = CrewTab.SCHEDULE) }
        builder.route(Routes.CREW_CHAT) { CrewChatEntryRoute() }
    }
}
