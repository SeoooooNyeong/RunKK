package com.a207.runkk.app.data.watch

import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.run.WatchResendRequester
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** WatchGateway·WatchResendRequester ← WatchLink(6.1.14, D-20, D-61, W40). */
@Module
@InstallIn(SingletonComponent::class)
abstract class WatchBindings {
    @Binds
    abstract fun watchGateway(impl: WatchLink): WatchGateway

    @Binds
    abstract fun watchResendRequester(impl: WatchLink): WatchResendRequester
}
