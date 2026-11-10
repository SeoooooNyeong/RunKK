package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.BackgroundUnlockSeenStore
import com.a207.runkk.app.domain.avatar.PendingSelfiePhoto
import com.a207.runkk.app.domain.avatar.SelfieJobFollower
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/**
 * AvatarRepository ← AvatarRepositoryImpl(D-61), PendingSelfieStore·BackgroundUnlockSeenStore ← SharedPreferences(S15P31A207-248, W8),
 * SelfieJobFollower ← WorkManager, PendingSelfiePhoto ← 앱 전용 파일(design-oo 05-30 [임시:D-성-117], [임시:D-성-119]).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AvatarBindings {
    @Binds
    abstract fun avatarRepository(impl: AvatarRepositoryImpl): AvatarRepository

    @Binds
    abstract fun pendingSelfieStore(impl: PreferencesPendingSelfieStore): PendingSelfieStore

    @Binds
    abstract fun selfieJobFollower(impl: WorkManagerSelfieJobFollower): SelfieJobFollower

    @Binds
    abstract fun pendingSelfiePhoto(impl: FilePendingSelfiePhoto): PendingSelfiePhoto

    @Binds
    abstract fun backgroundUnlockSeenStore(impl: PreferencesBackgroundUnlockSeenStore): BackgroundUnlockSeenStore

    companion object {
        @Provides
        fun avatarApi(retrofit: Retrofit): AvatarApi = retrofit.create()
    }
}
