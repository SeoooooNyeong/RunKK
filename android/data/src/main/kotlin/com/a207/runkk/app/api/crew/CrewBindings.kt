package com.a207.runkk.app.api.crew

import com.a207.runkk.app.domain.crew.CrewRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/** CrewRepository ← CrewRepositoryImpl(D-61). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class CrewBindings {
    @Binds
    abstract fun crewRepository(impl: CrewRepositoryImpl): CrewRepository

    companion object {
        @Provides
        fun crewApi(retrofit: Retrofit): CrewApi = retrofit.create()
    }
}
