package com.a207.runkk.app.ui.record

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

/** 기록 화면 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14). */
class RecordFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.RECORDS

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.RECORDS) { RecordListRoute() }
        builder.route(Routes.GROWTH) { GrowthRoute() }
        builder.route(Routes.REPORT) { ReportRoute() }
        builder.route(Routes.RANKING) { RankingRoute() }
        builder.route(Routes.COACH) { CoachRoute() }
        builder.addEntryProvider(Routes.RecordDetail::class) { key -> RecordDetailRoute(key.recordId) }
        builder.addEntryProvider(Routes.RecordCard::class) { key -> RecordCardRoute(key.recordId) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface RecordEntryModule {
    @Binds
    @IntoSet
    fun bindRecordEntry(entry: RecordFeatureEntry): FeatureEntry
}
