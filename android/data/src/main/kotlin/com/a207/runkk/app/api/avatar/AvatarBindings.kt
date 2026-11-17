package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.BackgroundUnlockSeenStore
import com.a207.runkk.app.domain.avatar.PendingSelfiePhoto
import com.a207.runkk.app.domain.avatar.SelfieJobFollower
import com.a207.runkk.app.domain.avatar.SelfieResultNotifier
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/**
 * AvatarRepository ← AvatarRepositoryImpl(D-61), PendingSelfieStore·BackgroundUnlockSeenStore ← SharedPreferences(S15P31A207-248, W8),
 * SelfieJobFollower ← WorkManager, PendingSelfiePhoto ← 앱 전용 파일(design-oo 05-30 D-749, D-751).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AvatarBindings {
    @Binds
    abstract fun avatarRepository(impl: AvatarRepositoryImpl): AvatarRepository

    @Binds
    abstract fun pendingSelfieStore(impl: PreferencesPendingSelfieStore): PendingSelfieStore

    /**
     * 셀카 결과 알림은 폰(:data:phone data.push)만 묶는다. 워치 앱도 공용 :data를 쓰므로 없을 수 있다(워치에서는 셀카를 보내지 않는다).
     */
    @BindsOptionalOf
    abstract fun selfieResultNotifier(): SelfieResultNotifier

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
