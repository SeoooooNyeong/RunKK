package com.a207.runkk.app.ui.me

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.ProviderType
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * 제공자 SDK로 로그인 토큰을 받는다(6.1.14). Activity가 필요한 화면 흐름이라 ui.me에 둔다.
 * 실패(취소·SDK 오류)는 던지지 않고 Outcome.Failure(Rejected(사유))로 돌려준다(13.2 오류 규칙).
 */
interface SocialLoginClient {
    val provider: ProviderType

    /** [context]는 SDK 로그인 화면을 띄울 Activity다. */
    suspend fun signIn(context: Context): Outcome<ProviderToken>
}

/**
 * 제공자 앱 키(빌드 속성 KAKAO_NATIVE_APP_KEY·GOOGLE_WEB_CLIENT_ID, local.properties → Gradle 속성 → 환경 변수 순).
 * 저장소에 넣지 않으며, 비어 있으면 그 로그인은 "준비 중"을 돌려준다.
 */
data class SocialLoginKeys(val kakaoNativeAppKey: String, val googleWebClientId: String) {
    override fun toString(): String =
        "SocialLoginKeys(kakao=${mask(kakaoNativeAppKey)}, google=${mask(googleWebClientId)})"

    private fun mask(key: String): String = if (key.isBlank()) "(없음)" else "***"
}

/**
 * 카카오 로그인(C-007). Kakao SDK는 Kakao 저장소(devrepo.kakao.com)에서 받는데, 그 저장소가 응답하지 않아(502) 이번에 SDK를
 * 붙이지 못했다. 앱 키·리다이렉트 스킴은 빌드 속성으로 받아 두었고(manifestPlaceholders kakaoScheme), SDK를 붙일 때 이 클래스의
 * signIn만 UserApiClient 호출로 바꾼다. 그 전에는 키가 있어도 "준비 중"을 돌려준다.
 */
class KakaoLoginClient @Inject constructor() : SocialLoginClient {
    override val provider: ProviderType = ProviderType.KAKAO

    override suspend fun signIn(context: Context): Outcome<ProviderToken> =
        Outcome.Failure(AppError.Rejected(SDK_NOT_READY))
}

/** 구글 ID 토큰을 받는 창구(Credential Manager). 시험에서는 가짜로 바꾼다. */
fun interface GoogleIdTokenSource {
    suspend fun idToken(context: Context, webClientId: String): Outcome<String>
}

/**
 * 구글 로그인(C-007): Credential Manager의 "Google로 로그인" 흐름으로 ID 토큰을 받아 서버 POST /auth/login에 넘긴다.
 * 웹 클라이언트 ID가 없으면 SDK를 부르지 않고 "준비 중"을 돌려준다(키 없이도 빌드·시험이 된다).
 */
class GoogleLoginClient @Inject constructor(
    private val keys: SocialLoginKeys,
    private val source: GoogleIdTokenSource
) : SocialLoginClient {
    override val provider: ProviderType = ProviderType.GOOGLE

    override suspend fun signIn(context: Context): Outcome<ProviderToken> {
        val clientId = keys.googleWebClientId.trim()
        if (clientId.isEmpty()) return Outcome.Failure(AppError.Rejected(SDK_NOT_READY))
        return when (val token = source.idToken(context, clientId)) {
            is Outcome.Success -> Outcome.Success(ProviderToken(ProviderType.GOOGLE, token.value))
            is Outcome.Failure -> token
        }
    }
}

/**
 * Credential Manager 구현. 취소는 LOGIN_CANCELLED, 고를 계정이 없거나 토큰을 읽지 못하면 AUTH_FAILED,
 * 그 밖의 Credential Manager 오류(Play 서비스 없음 등)는 PROVIDER_UNAVAILABLE.
 */
class CredentialManagerIdTokenSource @Inject constructor() : GoogleIdTokenSource {
    override suspend fun idToken(context: Context, webClientId: String): Outcome<String> {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Outcome.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Outcome.Failure(AppError.Rejected(AUTH_FAILED))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Outcome.Failure(AppError.Rejected(LOGIN_CANCELLED))
        } catch (e: NoCredentialException) {
            Outcome.Failure(AppError.Rejected(AUTH_FAILED))
        } catch (e: GetCredentialException) {
            Outcome.Failure(AppError.Rejected(PROVIDER_UNAVAILABLE))
        } catch (e: GoogleIdTokenParsingException) {
            Outcome.Failure(AppError.Rejected(AUTH_FAILED))
        }
    }

    private companion object {
        const val AUTH_FAILED = "AUTH_FAILED"
        const val PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE"
    }
}

/** SDK 연동 전 사유 코드(RunkkErrorText 문구 "이 로그인 방법은 아직 준비 중이에요"). */
internal const val SDK_NOT_READY = "PROVIDER_SDK_NOT_READY"

/** 사용자가 제공자 로그인 화면을 닫았다(RunkkErrorText "로그인을 취소했어요"). */
internal const val LOGIN_CANCELLED = "LOGIN_CANCELLED"
