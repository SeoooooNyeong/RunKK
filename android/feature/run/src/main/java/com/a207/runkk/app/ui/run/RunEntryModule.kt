package com.a207.runkk.app.ui.run

import com.a207.runkk.app.core.navigation.FeatureEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal interface RunEntryModule {
    @Binds
    @IntoSet
    fun bindRunEntry(entry: RunFeatureEntry): FeatureEntry
}
