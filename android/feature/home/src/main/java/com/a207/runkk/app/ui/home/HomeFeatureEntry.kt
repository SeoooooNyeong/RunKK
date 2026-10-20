package com.a207.runkk.app.ui.home

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/** 홈 화면 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14). */
class HomeFeatureEntry
@Inject
constructor() : FeatureEntry {
    override val route: String = Routes.HOME

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.addEntryProvider(Routes.Key(route)) { HomeScreen() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface HomeEntryModule {
    @Binds
    @IntoSet
    fun bindHomeEntry(entry: HomeFeatureEntry): FeatureEntry
}
