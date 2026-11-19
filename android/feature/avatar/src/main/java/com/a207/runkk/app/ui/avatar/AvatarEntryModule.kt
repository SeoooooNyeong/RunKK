package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.domain.avatar.MyAvatarPreparation
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

    /** 시작 로딩 화면의 홈 큰 아바타 미리 준비(S15P31A207-345 2차). */
    @Binds
    fun bindPreparation(impl: HomeAvatarPreparation): MyAvatarPreparation
}
