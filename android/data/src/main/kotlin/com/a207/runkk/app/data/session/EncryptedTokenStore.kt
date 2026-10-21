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
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * 서비스 토큰과 로그인 상태를 기기에 암호화해 보관한다(6.1.14 data.session, D-19).
 * SessionRepository(domain.account)·LoginState(domain.notification)·TokenProvider(api.core)를 한 객체가 구현하므로
 * SessionRepository.save와 TokenProvider.update가 같은 저장소를 쓰고, 어느 쪽에서 지우든 loggedIn이 거짓이 된다(H-25).
 * 저장은 EncryptedSharedPreferences(키는 Android Keystore의 AES256-GCM 마스터 키)이고 토큰 값은 로그에 쓰지 않는다.
 *
 * 메모리의 토큰 쌍이 기준이고 기기 저장은 그 사본이다. 저장소는 생성 때가 아니라 처음 쓰는 순간 연다. 열지 못하면(키 세트·마스터 키
 * 손상) 파일을, 그래도 안 되면 마스터 키까지 지우고 다시 열며, 끝내 안 되면 메모리에만 둔다(앱이 죽지 않고 다음 실행 때 다시 로그인).
 * 기기에 쓰지 못해도 갱신된 토큰은 메모리에 남아 서버가 돌린(rotate) 갱신 토큰을 이번 실행 동안 잃지 않는다.
 */
@Singleton
class EncryptedTokenStore internal constructor(private val openPreferences: () -> SharedPreferences?) :
    SessionRepository,
    TokenProvider,
    LoginState {
    @Inject
    constructor(@ApplicationContext context: Context) : this({ openEncrypted(context) })

    private val lock = Any()

    /** 기기 저장소. 열지 못했으면 null(메모리 전용). */
    private val prefs: SharedPreferences? by lazy { openPreferences() }

    /** 메모리의 토큰 쌍(접근, 갱신). 처음 읽을 때 기기 저장소에서 올린다. */
    private val memory: MutableStateFlow<Pair<String, String>?> by lazy { MutableStateFlow(readStored()) }

    private val _loggedIn: MutableStateFlow<Boolean> by lazy { MutableStateFlow(memory.value != null) }

    override val loggedIn: StateFlow<Boolean>
        get() = _loggedIn

    override fun accessToken(): String? = memory.value?.first

    override fun refreshToken(): String? = memory.value?.second

    /** 메모리를 바꾸고 기기에 쓴다. 기기 쓰기가 실패해도 예외를 던지지 않는다(이번 실행 동안은 메모리 토큰으로 이어 간다). */
    override fun update(access: String, refresh: String) {
        store(access, refresh)
    }

    override suspend fun save(tokens: SessionTokens): Outcome<Unit> = withContext(Dispatchers.IO) {
        if (store(tokens.access, tokens.refresh)) Outcome.Success(Unit) else Outcome.Failure(AppError.Storage)
    }

    /** 토큰을 지운다. 기기에 남은 값을 지우지 못해도 로그인 상태는 거짓으로 둔다(화면이 로그인으로 가도록). */
    override suspend fun clear(): Outcome<Unit> = withContext(Dispatchers.IO) {
        val removed =
            synchronized(lock) {
                memory.value = null
                _loggedIn.value = false
                persist { clear() }
            }
        if (removed) Outcome.Success(Unit) else Outcome.Failure(AppError.Storage)
    }

    /** 메모리에 두고 기기에 쓴다. 기기에 썼으면 참. */
    private fun store(access: String, refresh: String): Boolean = synchronized(lock) {
        memory.value = access to refresh
        _loggedIn.value = true
        persist { putString(KEY_ACCESS, access).putString(KEY_REFRESH, refresh) }
    }

    private fun readStored(): Pair<String, String>? {
        val store = prefs ?: return null
        val access = store.getString(KEY_ACCESS, null)
        val refresh = store.getString(KEY_REFRESH, null)
        return if (access != null && refresh != null) access to refresh else null
    }

    /** 기기 저장소에 쓴다. 저장소가 없거나 쓰기가 실패하면 거짓. lock 안에서만 부른다. */
    private fun persist(change: SharedPreferences.Editor.() -> SharedPreferences.Editor): Boolean {
        val store = prefs ?: return false
        return try {
            store.edit().change().commit()
        } catch (e: RuntimeException) {
            false
        }
    }

    private companion object {
        const val FILE = "runkk_session"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"

        /** androidx.security MasterKey의 기본 별칭(DEFAULT_MASTER_KEY_ALIAS). */
        const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"

        /**
         * 암호화 저장소를 연다. 키 세트가 깨졌으면(앱 재설치·백업 복원) 파일을 지우고, 그래도 안 되면 마스터 키도 지운 뒤 한 번 더 연다.
         * 끝내 열지 못하면 null(메모리 전용, 다시 로그인).
         */
        fun openEncrypted(context: Context): SharedPreferences? = attempt { create(context) }
            ?: run {
                context.deleteSharedPreferences(FILE)
                attempt { create(context) }
            }
            ?: run {
                context.deleteSharedPreferences(FILE)
                attempt { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS) }
                attempt { create(context) }
            }

        /** GeneralSecurityException·IOException·Keystore의 RuntimeException(ProviderException 등)을 모두 실패로 본다. */
        private inline fun <T> attempt(block: () -> T): T? = try {
            block()
        } catch (e: Exception) {
            null
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
