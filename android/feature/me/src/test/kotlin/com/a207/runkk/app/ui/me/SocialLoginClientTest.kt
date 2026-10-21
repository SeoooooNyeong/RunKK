package com.a207.runkk.app.ui.me

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.ProviderType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `카카오는 SDK를 붙이기 전까지 준비 중이고 키는 문자열로 드러나지 않는다`() = runTest {
        assertEquals(Outcome.Failure(AppError.Rejected(SDK_NOT_READY)), KakaoLoginClient().signIn(context))
        assertFalse("secret" in SocialLoginKeys("secret", "secret").toString())
    }
}
