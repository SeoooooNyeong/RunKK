package com.a207.runkk.app.ui.me

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/** 로그인·가입·마이 화면 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14). */
class MeFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.ME

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.LOGIN) { LoginRoute() }
        builder.route(Routes.SIGN_UP) { SignUpRoute() }
        builder.route(Routes.ME) { MeRoute() }
        builder.route(Routes.PROFILE_EDIT) { ProfileEditRoute() }
        builder.route(Routes.SETTINGS) { SettingsRoute(SettingsPage.APP) }
        builder.route(Routes.FREE_RUN_SETTINGS) { SettingsRoute(SettingsPage.FREE_RUN) }
        builder.route(Routes.NOTIFICATION_SETTINGS) { SettingsRoute(SettingsPage.NOTIFICATIONS) }
        builder.route(Routes.CONSENTS) { SettingsRoute(SettingsPage.CONSENTS) }
        builder.route(Routes.WITHDRAW) { WithdrawRoute() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface MeEntryModule {
    @Binds
    @IntoSet
    fun bindMeEntry(entry: MeFeatureEntry): FeatureEntry

    @Binds
    @IntoSet
    fun bindKakao(client: KakaoLoginClient): SocialLoginClient

    @Binds
    @IntoSet
    fun bindGoogle(client: GoogleLoginClient): SocialLoginClient

    @Binds
    fun bindGoogleIdTokenSource(source: CredentialManagerIdTokenSource): GoogleIdTokenSource

    @Binds
    fun bindKakaoAccessTokenSource(source: KakaoSdkTokenSource): KakaoAccessTokenSource

    companion object {
        /** 빌드 속성으로 받은 제공자 앱 키(없으면 빈 문자열 → "준비 중"). */
        @Provides
        fun socialLoginKeys(): SocialLoginKeys =
            SocialLoginKeys(BuildConfig.KAKAO_NATIVE_APP_KEY, BuildConfig.GOOGLE_WEB_CLIENT_ID)
    }
}
