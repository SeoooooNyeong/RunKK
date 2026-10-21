package com.a207.runkk.app.data.push

import com.a207.runkk.app.domain.notification.PushTokenSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** PushTokenSource ← FcmPushTokenSource(6.1.14, D-43, D-61). */
@Module
@InstallIn(SingletonComponent::class)
abstract class PushBindings {
    @Binds
    abstract fun pushTokenSource(impl: FcmPushTokenSource): PushTokenSource
}
