package com.a207.runkk.app.ui.record.runbody

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import com.a207.runkk.app.ui.record.coach.CoachChatRoute
import javax.inject.Inject

/**
 * 런바디 경로(Routes.REPORT)와 기록 하나의 런바디 상세(Routes.RunBodyDetail)를 등록한다. ui.record가 런바디 화면을 import하면 ui.record Fan-Out이 8이 되어(D-474와 같은 사정)
 * 하위 패키지가 따로 등록한다. 1차 배포 잠금은 그대로 Routes.REPORT 하나다(BetaLock, UnreleasedFeature.RUNBODY).
 */
class RunBodyFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.REPORT

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.REPORT) { RunBodyRoute() }
        builder.addEntryProvider(Routes.RunBodyDetail::class) { key -> RunBodyDetailRoute(key.recordId) }
        builder.route(Routes.COACH_CHAT) { CoachChatRoute() }
    }
}
