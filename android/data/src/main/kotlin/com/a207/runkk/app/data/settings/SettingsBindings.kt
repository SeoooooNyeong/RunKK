package com.a207.runkk.app.data.settings

import com.a207.runkk.app.domain.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** SettingsRepository ← DataStoreSettingsRepository(6.1.14, D-53, D-61). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsBindings {
    @Binds
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
