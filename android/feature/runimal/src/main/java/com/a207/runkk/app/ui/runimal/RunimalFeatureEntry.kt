package com.a207.runkk.app.ui.runimal

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import javax.inject.Inject

/** MY Runimal·16유형·유형 상세·평가 결과 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14). */
class RunimalFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.RUNIMAL

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.RUNIMAL) { RunimalRoute() }
        builder.route(Routes.RUNIMAL_TYPES) { RunimalTypesRoute() }
        builder.addEntryProvider(Routes.RunimalType::class) { key -> RunimalTypeRoute(key.type) }
        builder.addEntryProvider(Routes.Evaluation::class) { key ->
            EvaluationRoute(key.tier, key.validRuns, key.runimalType, key.previousTier)
        }
    }
}
