package com.a207.runkk.app.api.notification

import com.a207.runkk.app.domain.notification.EventRepository
import com.a207.runkk.app.domain.notification.NotificationRepository
import com.a207.runkk.app.domain.notification.PushTokenRemote
import com.a207.runkk.app.domain.notification.ReleaseLock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/**
 * NotificationRepository ← NotificationRepositoryImpl, PushTokenRemote ← PushTokenRemoteImpl, ReleaseLock ← BuildConfigReleaseLock
 * (D-43, D-61, D-339), EventRepository ← EventRepositoryImpl(이벤트 계약 2절).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationBindings {
    @Binds
    abstract fun notificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    abstract fun pushTokenRemote(impl: PushTokenRemoteImpl): PushTokenRemote

    @Binds
    abstract fun releaseLock(impl: BuildConfigReleaseLock): ReleaseLock

    @Binds
    abstract fun eventRepository(impl: EventRepositoryImpl): EventRepository

    companion object {
        @Provides
        fun notificationApi(retrofit: Retrofit): NotificationApi = retrofit.create()

        @Provides
        fun eventApi(retrofit: Retrofit): EventApi = retrofit.create()
    }
}
