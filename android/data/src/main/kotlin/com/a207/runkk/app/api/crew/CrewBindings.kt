package com.a207.runkk.app.api.crew

import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.sync.AssetPreloadSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import retrofit2.create

/** CrewRepository ← CrewRepositoryImpl(D-61). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class CrewBindings {
    @Binds
    abstract fun crewRepository(impl: CrewRepositoryImpl): CrewRepository

    /** 시작 로딩 화면의 미리 받기 출처: 크루 탭 첫 화면(S15P31A207-345 2차). */
    @Binds
    @IntoSet
    abstract fun preloadSource(impl: CrewPreloadSource): AssetPreloadSource

    companion object {
        @Provides
        fun crewApi(retrofit: Retrofit): CrewApi = retrofit.create()
    }
}
