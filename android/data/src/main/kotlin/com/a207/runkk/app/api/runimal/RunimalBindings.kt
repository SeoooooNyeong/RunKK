package com.a207.runkk.app.api.runimal

import com.a207.runkk.app.domain.runimal.RunimalRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/** RunimalRepository ← RunimalRepositoryImpl(D-61). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RunimalBindings {
    @Binds
    abstract fun runimalRepository(impl: RunimalRepositoryImpl): RunimalRepository

    companion object {
        @Provides
        fun runimalApi(retrofit: Retrofit): RunimalApi = retrofit.create()
    }
}
