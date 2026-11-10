package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.BackgroundUnlockSeenStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/** AvatarRepository ← AvatarRepositoryImpl(D-61), PendingSelfieStore·BackgroundUnlockSeenStore ← SharedPreferences(S15P31A207-248, W8). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AvatarBindings {
    @Binds
    abstract fun avatarRepository(impl: AvatarRepositoryImpl): AvatarRepository

    @Binds
    abstract fun pendingSelfieStore(impl: PreferencesPendingSelfieStore): PendingSelfieStore

    @Binds
    abstract fun backgroundUnlockSeenStore(impl: PreferencesBackgroundUnlockSeenStore): BackgroundUnlockSeenStore

    companion object {
        @Provides
        fun avatarApi(retrofit: Retrofit): AvatarApi = retrofit.create()
    }
}
