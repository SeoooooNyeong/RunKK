package com.a207.runkk.app.ui.runimal

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import javax.inject.Inject

/** MY Runimal 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14). */
class RunimalFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.RUNIMAL

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.RUNIMAL) { RunimalRoute() }
    }
}
