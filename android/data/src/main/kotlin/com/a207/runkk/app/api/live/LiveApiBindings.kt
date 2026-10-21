package com.a207.runkk.app.api.live

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/** LiveApi를 인증된 Retrofit으로 만든다(5.1.2 api.live). 도메인 포트가 없으므로 @Binds는 없다. */
@Module
@InstallIn(SingletonComponent::class)
object LiveApiBindings {
    @Provides
    fun liveApi(retrofit: Retrofit): LiveApi = retrofit.create()
}
