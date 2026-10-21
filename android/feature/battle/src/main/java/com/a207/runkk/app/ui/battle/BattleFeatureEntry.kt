package com.a207.runkk.app.ui.battle

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * 대결 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14): 셀프 탭(나와의 싸움), 셀럽 탭(유명인), 배틀런 매칭,
 * 대결 진행·결과, 함께 달린 기록.
 */
class BattleFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.BATTLE_RANDOM

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.SELF) { SelfBattleRoute() }
        builder.route(Routes.CELEB) { CelebBattleRoute() }
        builder.route(Routes.BATTLE_RANDOM) { MatchRoute() }
        builder.route(Routes.BATTLE_QUICK_MATCH) { MatchRoute(isQuick = true) }
        builder.route(Routes.BATTLE_HISTORY) { BattleHistoryRoute() }
        builder.addEntryProvider(Routes.BattleRunning::class) { key -> BattleRunningRoute(key) }
        builder.addEntryProvider(Routes.BattleResult::class) { key -> BattleResultRoute(key.battleId) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface BattleEntryModule {
    @Binds
    @IntoSet
    fun bindBattleEntry(entry: BattleFeatureEntry): FeatureEntry
}
