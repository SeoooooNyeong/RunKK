package com.a207.runkk.app.api.record

import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.sync.AssetPreloadSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import retrofit2.create

/** RecordRepository ← RecordRepositoryImpl(D-61). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RecordBindings {
    @Binds
    abstract fun recordRepository(impl: RecordRepositoryImpl): RecordRepository

    /** 시작 로딩 화면의 미리 받기 출처: 랭킹·기록 첫 화면(S15P31A207-345 2차). */
    @Binds
    @IntoSet
    abstract fun preloadSource(impl: RecordPreloadSource): AssetPreloadSource

    companion object {
        @Provides
        fun recordApi(retrofit: Retrofit): RecordApi = retrofit.create()
    }
}
