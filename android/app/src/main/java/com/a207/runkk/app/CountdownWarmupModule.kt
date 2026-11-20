package com.a207.runkk.app

import com.a207.runkk.app.domain.sync.AssetPreloadSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 카운트다운 데우기 출처를 시작 미리 받기 출처 집합에 넣는다(PreloadModule과 따로 둔다). */
@Module
@InstallIn(SingletonComponent::class)
internal interface CountdownWarmupModule {
    @Binds
    @IntoSet
    fun countdownWarmup(source: CountdownWarmupSource): AssetPreloadSource
}
