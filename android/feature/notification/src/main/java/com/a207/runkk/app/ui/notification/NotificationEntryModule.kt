package com.a207.runkk.app.ui.notification

import androidx.compose.runtime.getValue
import com.a207.runkk.app.core.navigation.FeatureEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal interface NotificationEntryModule {
    @Binds
    @IntoSet
    fun bindNotificationEntry(entry: NotificationFeatureEntry): FeatureEntry
}
