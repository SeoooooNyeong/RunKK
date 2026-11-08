package com.a207.runkk.app.ui.record.runbody

import com.a207.runkk.app.core.navigation.FeatureEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal interface RunBodyEntryModule {
    @Binds
    @IntoSet
    fun bindRunBodyEntry(entry: RunBodyFeatureEntry): FeatureEntry
}
