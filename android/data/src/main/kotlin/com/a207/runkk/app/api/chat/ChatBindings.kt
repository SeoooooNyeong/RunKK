package com.a207.runkk.app.api.chat

import com.a207.runkk.app.domain.chat.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/** ChatRepository ← ChatRepositoryImpl(6.1.14, F.6.4, D-61). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ChatBindings {
    @Binds
    abstract fun chatRepository(impl: ChatRepositoryImpl): ChatRepository

    companion object {
        @Provides
        fun chatApi(retrofit: Retrofit): ChatApi = retrofit.create()
    }
}
