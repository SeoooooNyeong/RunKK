package com.a207.runkk.app.api.coach

import com.a207.runkk.app.domain.coach.CoachRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CoachBindings {
    @Binds
    abstract fun coachRepository(impl: CoachRepositoryImpl): CoachRepository

    companion object {
        @Provides
        @Singleton
        fun coachApi(retrofit: Retrofit, client: OkHttpClient): CoachApi = retrofit.newBuilder()
            .client(
                client.newBuilder()
                    .readTimeout(65, TimeUnit.SECONDS)
                    .callTimeout(75, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false)
                    .build()
            )
            .build()
            .create()
    }
}
