package com.a207.runkk.app.ui.battle

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.core.navigation.route
import javax.inject.Inject

/**
 * 대결 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14): 기록런 탭(셀프런 | 셀럽런), 배틀런 화면(랜덤 | 친구 대결),
 * 매칭 대기·친구 초대 대기·초대 참가 확인, 대결 진행·결과. 결과에 티어 평가가 실리면 평가 화면(Routes.Evaluation)을 한 번 연다(D-284).
 * 기록 화면 "대결" 탭 칸(SlotKey.RECORD_BATTLES)에 대결 이력 탭을 넣는다(D-281, D-298).
 * 앱 화면 위에는 서버에 남은 진행 중 대결 안내를 붙인다(배틀 QA 7번).
 */
class BattleFeatureEntry @Inject constructor(private val sharer: InviteSharer) : FeatureEntry {
    override val route: String = Routes.BATTLE_RANDOM

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.RECORD_RUN) { RecordRunRoute() }
        builder.route(Routes.BATTLE_MODE) { BattleModeRoute() }
        builder.addEntryProvider(Routes.BattleQuickMatch::class) { key ->
            MatchRoute(MatchStart.Quick(Distance(key.distanceMeters)), sharer)
        }
        builder.addEntryProvider(Routes.FriendInviteWait::class) { key ->
            MatchRoute(MatchStart.Invite(Distance(key.distanceMeters)), sharer)
        }
        builder.addEntryProvider(Routes.FriendInviteJoin::class) { key ->
            MatchRoute(MatchStart.Join(key.token), sharer)
        }
        builder.addEntryProvider(Routes.BattleRunning::class) { key -> BattleRunningRoute(key) }
        builder.addEntryProvider(Routes.BattleResult::class) { key ->
            val navigator = LocalRunkkNavigator.current
            BattleResultRoute(
                battleId = key.battleId,
                onEvaluation = { navigator.navigate(Routes.Evaluation(it.tier, it.validRuns, it.runimalType)) }
            )
        }
    }

    override val slots: Map<SlotKey, @Composable () -> Unit> = mapOf(SlotKey.RECORD_BATTLES to { BattleHistoryTab() })

    @Composable
    override fun Overlay(top: NavKey?) = ActiveBattleOverlay()
}
