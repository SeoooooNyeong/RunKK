package com.a207.runkk.app.api.account

import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.settings.GuideRemote
import com.a207.runkk.app.domain.settings.SettingsRemote
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/** AccountRepository ← AccountRepositoryImpl, SettingsRemote ← SettingsRemoteImpl(6.1.14 예시 그대로, D-61), GuideRemote ← GuideRemoteImpl(D-300). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AccountBindings {
    @Binds
    abstract fun accountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    abstract fun settingsRemote(impl: SettingsRemoteImpl): SettingsRemote

    @Binds
    abstract fun guideRemote(impl: GuideRemoteImpl): GuideRemote

    companion object {
        @Provides
        fun authApi(retrofit: Retrofit): AuthApi = retrofit.create()

        @Provides
        fun meApi(retrofit: Retrofit): MeApi = retrofit.create()
    }
}
