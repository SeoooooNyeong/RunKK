package com.a207.runkk.app.api.battle

import com.a207.runkk.app.domain.battle.BattleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/** BattleRepository ← BattleRepositoryImpl(D-61). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class BattleBindings {
    @Binds
    abstract fun battleRepository(impl: BattleRepositoryImpl): BattleRepository

    companion object {
        @Provides
        fun battleApi(retrofit: Retrofit): BattleApi = retrofit.create()
    }
}
