package com.a207.runkk.app.api.battle

import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.PendingForfeitStore
import com.a207.runkk.app.domain.sync.AssetPreloadSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import retrofit2.create

/** BattleRepository ← BattleRepositoryImpl(D-61), PendingForfeitStore ← SharedPreferences(S15P31A207-278). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class BattleBindings {
    @Binds
    abstract fun battleRepository(impl: BattleRepositoryImpl): BattleRepository

    @Binds
    abstract fun pendingForfeitStore(impl: PreferencesPendingForfeitStore): PendingForfeitStore

    /** 시작 로딩 화면의 미리 받기 출처: 셀럽런·친구 첫 화면(S15P31A207-345 2차). */
    @Binds
    @IntoSet
    abstract fun preloadSource(impl: BattlePreloadSource): AssetPreloadSource

    companion object {
        @Provides
        fun battleApi(retrofit: Retrofit): BattleApi = retrofit.create()
    }
}
