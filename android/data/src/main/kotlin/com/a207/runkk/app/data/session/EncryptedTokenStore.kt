package com.a207.runkk.app.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.notification.LoginState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 서비스 토큰과 로그인 상태를 기기에 암호화해 보관한다(6.1.14 data.session, D-19).
 * SessionRepository(domain.account)·LoginState(domain.notification)·TokenProvider(api.core)를 한 객체가 구현하므로
 * SessionRepository.save와 TokenProvider.update가 같은 저장소를 쓰고, 어느 쪽에서 지우든 loggedIn이 거짓이 된다(H-25).
 * 저장은 EncryptedSharedPreferences(키는 Android Keystore의 AES256-GCM 마스터 키)이고 토큰 값은 로그에 쓰지 않는다.
 */
@Singleton
class EncryptedTokenStore internal constructor(preferences: () -> SharedPreferences) :
    SessionRepository,
    TokenProvider,
    LoginState {
    @Inject
    constructor(@ApplicationContext context: Context) : this({ openEncrypted(context) })

    private val prefs: SharedPreferences by lazy(preferences)
    private val lock = Any()
    private val _loggedIn = MutableStateFlow(prefs.getString(KEY_ACCESS, null) != null)

    override val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    override fun accessToken(): String? = prefs.getString(KEY_ACCESS, null)

    override fun refreshToken(): String? = prefs.getString(KEY_REFRESH, null)

    override fun update(access: String, refresh: String) {
        synchronized(lock) {
            check(prefs.edit().putString(KEY_ACCESS, access).putString(KEY_REFRESH, refresh).commit()) {
                "토큰을 저장하지 못했다"
            }
            _loggedIn.value = true
        }
    }

    override suspend fun save(tokens: SessionTokens): Outcome<Unit> = withContext(Dispatchers.IO) {
        try {
            update(tokens.access, tokens.refresh)
            Outcome.Success(Unit)
        } catch (e: IllegalStateException) {
            Outcome.Failure(AppError.Storage)
        }
    }

    /** 토큰을 지운다. 기기에 남은 값을 지우지 못해도 로그인 상태는 거짓으로 둔다(화면이 로그인으로 가도록). */
    override suspend fun clear(): Outcome<Unit> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val removed = prefs.edit().clear().commit()
            _loggedIn.value = false
            if (removed) Outcome.Success(Unit) else Outcome.Failure(AppError.Storage)
        }
    }

    private companion object {
        const val FILE = "runkk_session"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"

        /** 암호화 저장소를 연다. 키 세트가 깨졌으면(앱 재설치·백업 복원) 파일을 지우고 한 번 더 연다(다시 로그인). */
        fun openEncrypted(context: Context): SharedPreferences = try {
            create(context)
        } catch (e: GeneralSecurityException) {
            context.deleteSharedPreferences(FILE)
            create(context)
        } catch (e: IOException) {
            context.deleteSharedPreferences(FILE)
            create(context)
        }

        @Suppress("DEPRECATION") // security-crypto 1.1.0은 API를 더 고치지 않는다고 표시했지만 설계(6.1.14)가 정한 저장 방식이다.
        private fun create(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
