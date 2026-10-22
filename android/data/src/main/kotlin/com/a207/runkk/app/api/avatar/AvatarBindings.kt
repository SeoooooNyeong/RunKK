package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.domain.avatar.AvatarRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/** AvatarRepository ← AvatarRepositoryImpl(D-61). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AvatarBindings {
    @Binds
    abstract fun avatarRepository(impl: AvatarRepositoryImpl): AvatarRepository

    companion object {
        @Provides
        fun avatarApi(retrofit: Retrofit): AvatarApi = retrofit.create()
    }
}
