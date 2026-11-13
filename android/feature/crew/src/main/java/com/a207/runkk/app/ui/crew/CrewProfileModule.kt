package com.a207.runkk.app.ui.crew

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** ProfileVisibilitySaver ← AccountProfileVisibilitySaver(F-23 내 정보 시트의 공개 토글, W7). */
@Module
@InstallIn(SingletonComponent::class)
internal interface CrewProfileModule {
    @Binds
    fun bindProfileVisibilitySaver(impl: AccountProfileVisibilitySaver): ProfileVisibilitySaver
}
