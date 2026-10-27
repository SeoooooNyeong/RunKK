package com.a207.runkk.app.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.SessionTokens
import kotlin.coroutines.CoroutineContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** EncryptedTokenStore 게스트 자격 증명(6.1.14 data.session, D-89, D-159): 같은 저장소에 두고 clear가 함께 지운다. */
@RunWith(RobolectricTestRunner::class)
class EncryptedTokenStoreTest {
    private lateinit var prefs: SharedPreferences

    @BeforeTest
    fun setUp() {
        prefs =
            ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(
                "store-test",
                Context.MODE_PRIVATE
            )
        prefs.edit().clear().commit()
    }

    private fun store() = EncryptedTokenStore { prefs }

    @Test
    fun `게스트 자격 증명을 저장하면 다시 열어도 남아 있다`() = runTest {
        val store = store()
        assertFalse(store.hasGuestCredential())

        assertEquals(Outcome.Success(Unit), store.saveGuestCredential("g1.42.secret"))

        assertTrue(store.hasGuestCredential())
        assertEquals("g1.42.secret", store().guestCredential())
    }

    @Test
    fun `clear는 토큰과 게스트 자격 증명을 함께 지운다`() = runTest {
        val store = store()
        store.save(SessionTokens("a", "r"))
        store.saveGuestCredential("g1.42.secret")

        store.clear()

        assertFalse(store.hasGuestCredential())
        assertFalse(store.loggedIn.value)
        assertNull(store().guestCredential())
    }

    @Test
    fun `AN5-C-8 세대를 읽은 뒤 clear가 있었으면 updateInSession은 저장하지 않고 같은 세대면 저장한다`() = runTest {
        val store = store()
        store.save(SessionTokens("a", "r"))
        val before = store.sessionGeneration()

        store.clear()

        assertFalse(store.updateInSession(before, "a2", "r2"))
        assertFalse(store.loggedIn.value)
        assertNull(store.accessToken())
        assertTrue(store.updateInSession(store.sessionGeneration(), "a3", "r3"))
        assertEquals("a3", store.accessToken())
        assertTrue(store.loggedIn.value)
    }

    @Test
    fun `AN6-2 세대를 읽은 뒤 save로 토큰이 바뀌었으면 updateInSession은 새 토큰을 덮어쓰지 않는다`() = runTest {
        val store = store()
        store.save(SessionTokens("a", "r"))
        val before = store.sessionGeneration()

        store.save(SessionTokens("login-a", "login-r"))

        assertFalse(store.updateInSession(before, "late-a", "late-r"))
        assertEquals("login-a", store.accessToken())
        assertEquals("login-r", store.refreshToken())
        assertTrue(store.loggedIn.value)
    }

    @Test
    fun `AN5-C-16 게스트 자격 증명 확인은 저장소의 IO 디스패처로 옮겨 읽는다`() = runTest {
        val io = CountingDispatcher(StandardTestDispatcher(testScheduler))
        val store = EncryptedTokenStore(io) { prefs }

        assertFalse(store.hasGuestCredential())

        assertTrue(io.dispatches > 0, "첫 호출이 암호화 저장소를 열 수 있어 부르는 스레드가 아니라 IO에서 읽는다")
    }

    /** 몇 번 옮겨 실행했는지 센다. */
    private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var dispatches = 0

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }

    @Test
    fun `clearGuestCredential은 토큰과 로그인 상태를 그대로 둔다`() = runTest {
        val store = store()
        store.save(SessionTokens("a", "r"))
        store.saveGuestCredential("g1.42.secret")

        assertEquals(Outcome.Success(Unit), store.clearGuestCredential())

        assertFalse(store.hasGuestCredential())
        assertTrue(store.loggedIn.value)
        assertEquals("a", store.accessToken())
        assertFalse(store().hasGuestCredential())
    }

    @Test
    fun `기기 저장소를 열지 못하면 저장은 Storage 실패다`() = runTest {
        val memoryOnly = EncryptedTokenStore { null }

        assertEquals(Outcome.Failure(AppError.Storage), memoryOnly.saveGuestCredential("g1.42.secret"))
    }
}
