package com.a207.runkk.app.api.notification

import com.a207.runkk.app.domain.notification.NotificationRepository
import com.a207.runkk.app.domain.notification.PushTokenRemote
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/** NotificationRepository ← NotificationRepositoryImpl, PushTokenRemote ← PushTokenRemoteImpl(D-43, D-61). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationBindings {
    @Binds
    abstract fun notificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    abstract fun pushTokenRemote(impl: PushTokenRemoteImpl): PushTokenRemote

    companion object {
        @Provides
        fun notificationApi(retrofit: Retrofit): NotificationApi = retrofit.create()
    }
}
