package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.navigation.FeatureEntry
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

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

    /** 대체 창구 자리. 출시 코드에서는 비어 있다(KakaoTokenOverride 참고). */
    @Multibinds
    @KakaoTokenOverride
    fun kakaoTokenOverrides(): Set<KakaoAccessTokenSource>

    companion object {
        @Provides
        fun kakaoAccessTokenSource(
            @KakaoTokenOverride overrides: Set<@JvmSuppressWildcards KakaoAccessTokenSource>,
            sdk: KakaoSdkTokenSource
        ): KakaoAccessTokenSource = chooseKakaoTokenSource(overrides, sdk)

        /** 빌드 속성으로 받은 제공자 앱 키(없으면 빈 문자열 → "준비 중"). */
        @Provides
        fun socialLoginKeys(): SocialLoginKeys =
            SocialLoginKeys(BuildConfig.KAKAO_NATIVE_APP_KEY, BuildConfig.GOOGLE_WEB_CLIENT_ID)
    }
}
