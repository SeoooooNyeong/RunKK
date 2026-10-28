package com.a207.runkk.app.api.record

import com.a207.runkk.app.domain.record.RecordRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/** RecordRepository ← RecordRepositoryImpl(D-61). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RecordBindings {
    @Binds
    abstract fun recordRepository(impl: RecordRepositoryImpl): RecordRepository

    companion object {
        @Provides
        fun recordApi(retrofit: Retrofit): RecordApi = retrofit.create()
    }
}
