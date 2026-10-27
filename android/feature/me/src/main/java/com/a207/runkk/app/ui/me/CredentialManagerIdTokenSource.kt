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
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

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
