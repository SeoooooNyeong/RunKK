package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.navigation.FeatureEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal interface AvatarEntryModule {
    @Binds
    @IntoSet
    fun bindAvatarEntry(entry: AvatarFeatureEntry): FeatureEntry
}
