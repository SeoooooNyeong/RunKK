package com.a207.runkk.app.api.battle

import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.PendingForfeitStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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

    companion object {
        @Provides
        fun battleApi(retrofit: Retrofit): BattleApi = retrofit.create()
    }
}
