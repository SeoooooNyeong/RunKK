package com.a207.runkk.app.api.report

import com.a207.runkk.app.domain.report.ReportRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/** ReportRepository ← ReportRepositoryImpl(6.1.14, F.6.4, D-61). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReportBindings {
    @Binds
    abstract fun reportRepository(impl: ReportRepositoryImpl): ReportRepository

    companion object {
        @Provides
        fun reportApi(retrofit: Retrofit): ReportApi = retrofit.create()
    }
}
