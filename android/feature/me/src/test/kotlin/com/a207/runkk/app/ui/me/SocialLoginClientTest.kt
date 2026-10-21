package com.a207.runkk.app.ui.me

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.ProviderType
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 소셜 로그인 클라이언트(C-007): 키가 없으면 SDK를 부르지 않고 "준비 중", 있으면 제공자 토큰을 넘긴다. */
@RunWith(RobolectricTestRunner::class)
class SocialLoginClientTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private class RecordingSource(private val result: Outcome<String>) : GoogleIdTokenSource {
        val clientIds = mutableListOf<String>()

        override suspend fun idToken(context: Context, webClientId: String): Outcome<String> {
            clientIds += webClientId
            return result
        }
    }

    @Test
    fun `구글 웹 클라이언트 ID가 없으면 SDK를 부르지 않고 준비 중`() = runTest {
        val source = RecordingSource(Outcome.Success("id-token"))
        val client = GoogleLoginClient(SocialLoginKeys(kakaoNativeAppKey = "", googleWebClientId = " "), source)

        assertEquals(Outcome.Failure(AppError.Rejected(SDK_NOT_READY)), client.signIn(context))
        assertEquals(emptyList(), source.clientIds)
    }

    @Test
    fun `구글 ID 토큰을 받으면 제공자 토큰으로 넘긴다`() = runTest {
        val source = RecordingSource(Outcome.Success("id-token"))
        val client = GoogleLoginClient(SocialLoginKeys("", "web-client.apps.googleusercontent.com"), source)

        assertEquals(Outcome.Success(ProviderToken(ProviderType.GOOGLE, "id-token")), client.signIn(context))
        assertEquals(listOf("web-client.apps.googleusercontent.com"), source.clientIds)
    }

    @Test
    fun `구글 로그인을 취소하면 취소 사유를 그대로 돌려준다`() = runTest {
        val client = GoogleLoginClient(
            SocialLoginKeys("", "web-client"),
            RecordingSource(Outcome.Failure(AppError.Rejected(LOGIN_CANCELLED)))
        )

        assertEquals(Outcome.Failure(AppError.Rejected(LOGIN_CANCELLED)), client.signIn(context))
    }

    @Test
    fun `카카오 네이티브 앱 키가 없으면 SDK를 부르지 않고 준비 중, 키는 문자열로 드러나지 않는다`() = runTest {
        val appKeys = mutableListOf<String>()
        val client = KakaoLoginClient(SocialLoginKeys("", "")) { _, key ->
            appKeys += key
            Outcome.Success("access")
        }

        assertEquals(Outcome.Failure(AppError.Rejected(SDK_NOT_READY)), client.signIn(context))
        assertEquals(emptyList(), appKeys)
        assertFalse("secret" in SocialLoginKeys("secret", "secret").toString())
    }

    @Test
    fun `카카오 접근 토큰을 받으면 제공자 토큰으로, 취소는 취소 사유로`() = runTest {
        val ok = KakaoLoginClient(SocialLoginKeys("native-key", "")) { _, key ->
            assertEquals("native-key", key)
            Outcome.Success("kakao-access")
        }
        val cancelled = KakaoLoginClient(SocialLoginKeys("native-key", "")) { _, _ ->
            Outcome.Failure(AppError.Rejected(LOGIN_CANCELLED))
        }

        assertEquals(Outcome.Success(ProviderToken(ProviderType.KAKAO, "kakao-access")), ok.signIn(context))
        assertEquals(Outcome.Failure(AppError.Rejected(LOGIN_CANCELLED)), cancelled.signIn(context))
    }

    /** 키 없이 토큰을 주는 대체 창구(E2E 시험용, :app localDebug만 넣는다). */
    private class KeylessSource(private val token: String) : KakaoAccessTokenSource {
        val appKeys = mutableListOf<String>()
        override val requiresNativeAppKey: Boolean = false

        override suspend fun accessToken(context: Context, nativeAppKey: String): Outcome<String> {
            appKeys += nativeAppKey
            return Outcome.Success(token)
        }
    }

    @Test
    fun `앱 키가 필요 없는 대체 창구는 키가 없어도 토큰을 받는다`() = runTest {
        val source = KeylessSource("e2e-1001")
        val client = KakaoLoginClient(SocialLoginKeys("", ""), source)

        assertEquals(Outcome.Success(ProviderToken(ProviderType.KAKAO, "e2e-1001")), client.signIn(context))
        assertEquals(listOf(""), source.appKeys)
    }

    @Test
    fun `대체 창구가 없으면 Kakao SDK 창구, 하나면 그것, 둘 이상이면 설정 오류`() {
        val sdk = KakaoAccessTokenSource { _, _ -> Outcome.Success("sdk") }
        val override = KeylessSource("e2e-1")

        assertSame(sdk, chooseKakaoTokenSource(emptySet(), sdk))
        assertSame(override, chooseKakaoTokenSource(setOf(override), sdk))
        assertFailsWith<IllegalStateException> {
            chooseKakaoTokenSource(setOf(override, KeylessSource("e2e-2")), sdk)
        }
    }
}
