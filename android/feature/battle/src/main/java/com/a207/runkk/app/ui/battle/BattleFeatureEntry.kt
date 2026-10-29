package com.a207.runkk.app.ui.battle

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.core.navigation.route
import javax.inject.Inject

/**
 * 대결 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14): 셀프 탭(나와의 싸움), 셀럽 탭(유명인), 배틀런 매칭,
 * 대결 진행·결과. 기록 화면 "대결" 탭 칸(SlotKey.RECORD_BATTLES)에 대결 이력 탭을 넣는다(D-281, D-298).
 * 앱 화면 위에는 서버에 남은 진행 중 대결 안내를 붙인다(배틀 QA 7번).
 */
class BattleFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.BATTLE_RANDOM

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.RECORD_RUN) { RecordRunRoute() }
        builder.route(Routes.SELF) { SelfBattleRoute() }
        builder.route(Routes.CELEB) { CelebBattleRoute() }
        builder.route(Routes.BATTLE_RANDOM) { MatchRoute() }
        builder.addEntryProvider(Routes.BattleQuickMatch::class) { key ->
            MatchRoute(quickDistance = Distance(key.distanceMeters))
        }
        builder.addEntryProvider(Routes.BattleRunning::class) { key -> BattleRunningRoute(key) }
        builder.addEntryProvider(Routes.BattleResult::class) { key -> BattleResultRoute(key.battleId) }
    }

    override val slots: Map<SlotKey, @Composable () -> Unit> = mapOf(SlotKey.RECORD_BATTLES to { BattleHistoryTab() })

    @Composable
    override fun Overlay(top: NavKey?) = ActiveBattleOverlay()
}
