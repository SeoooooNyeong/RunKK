package com.a207.runkk.app.ui.run

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

/** 자유런 측정·결과 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14). */
class RunFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.RUN

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.RUN) { RunRoute() }
        builder.addEntryProvider(Routes.RunResult::class) { key -> RunResultRoute(key.recordId) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface RunEntryModule {
    @Binds
    @IntoSet
    fun bindRunEntry(entry: RunFeatureEntry): FeatureEntry
}
