package com.a207.runkk.app.ui.home

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import javax.inject.Inject

/** 홈 화면 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14). */
class HomeFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.HOME

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.HOME) { HomeRoute() }
    }
}
