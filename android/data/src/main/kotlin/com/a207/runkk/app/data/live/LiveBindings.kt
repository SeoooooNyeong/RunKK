package com.a207.runkk.app.data.live

import com.a207.runkk.app.domain.live.LiveChannelProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** LiveChannelProvider ← FallbackLiveChannelProvider(6.1.14, D-57, D-61). 채널은 Provider가 만들므로 LiveChannel 바인딩은 없다. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LiveBindings {
    @Binds
    abstract fun liveChannelProvider(impl: FallbackLiveChannelProvider): LiveChannelProvider
}
