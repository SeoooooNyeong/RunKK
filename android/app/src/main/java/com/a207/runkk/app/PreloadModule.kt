package com.a207.runkk.app

import com.a207.runkk.app.domain.sync.AssetPreloadSource
import com.a207.runkk.app.domain.sync.AvatarPrepareSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 시작 에셋 미리 받기 출처 중 도메인에 있는 것(홈 큰 아바타 합성, domain.sync.AvatarPrepareSource)을 출처 집합에 넣는다(S15P31A207-345
 * 2차). 데이터 출처(내 머리·랭킹·기록·셀럽런·친구·크루·홈 요약)는 각 api 패키지 모듈이 넣는다. 폰 앱에만 있다(워치는 미리 받지 않는다).
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface PreloadModule {
    @Binds
    @IntoSet
    fun avatarPrepare(source: AvatarPrepareSource): AssetPreloadSource
}
