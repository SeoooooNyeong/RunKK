package com.a207.runkk.app.data.push

import com.a207.runkk.app.domain.chat.OpenChatRoomRegistry
import com.a207.runkk.app.domain.notification.PushTokenSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** PushTokenSource ← FcmPushTokenSource, OpenChatRoomRegistry ← OpenChatRoomTracker(6.1.14, D-43, D-61, D-130). */
@Module
@InstallIn(SingletonComponent::class)
abstract class PushBindings {
    @Binds
    abstract fun pushTokenSource(impl: FcmPushTokenSource): PushTokenSource

    @Binds
    abstract fun openChatRoomRegistry(impl: OpenChatRoomTracker): OpenChatRoomRegistry
}
