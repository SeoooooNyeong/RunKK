package com.a207.runkk.app.data.session

import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.notification.LoginState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** SessionRepository·TokenProvider·LoginState ← EncryptedTokenStore(같은 @Singleton 인스턴스, 6.1.14, D-61). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SessionBindings {
    @Binds
    abstract fun sessionRepository(impl: EncryptedTokenStore): SessionRepository

    @Binds
    abstract fun tokenProvider(impl: EncryptedTokenStore): TokenProvider

    @Binds
    abstract fun loginState(impl: EncryptedTokenStore): LoginState
}
