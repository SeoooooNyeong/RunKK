package com.a207.runkk.app.api.runbody

import com.a207.runkk.app.domain.runbody.RunBodyRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/** RunBodyRepository ← RunBodyRepositoryImpl(D-61, D-453). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RunBodyBindings {
    @Binds
    abstract fun runBodyRepository(impl: RunBodyRepositoryImpl): RunBodyRepository

    companion object {
        @Provides
        fun runBodyApi(retrofit: Retrofit): RunBodyApi = retrofit.create()
    }
}
