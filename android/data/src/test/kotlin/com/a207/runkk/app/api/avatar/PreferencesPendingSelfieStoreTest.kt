package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.api.core.jwtOf
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.SelfieFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 남긴 셀카 작업은 앱을 다시 켜도 남고, 접수한 회원에게만 보인다(S15P31A207-248). */
@RunWith(RobolectricTestRunner::class)
class PreferencesPendingSelfieStoreTest {
    private val tokens = Tokens(jwtOf("7"))

    private fun store() = PreferencesPendingSelfieStore(RuntimeEnvironment.getApplication(), SelfMember(tokens))

    @Test
    fun `앱을 다시 켜도 같은 회원이면 남긴 작업을 돌려준다`() {
        store().save("job-1")
        assertEquals("job-1", store().load())
        store().clear()
        assertNull(store().load())
    }

    @Test
    fun `다른 회원으로 로그인했거나 로그아웃이면 없는 것으로 본다`() {
        store().save("job-1")
        tokens.access = jwtOf("8")
        assertNull(store().load())
        tokens.access = null
        assertNull(store().load())
        store().save("job-2")
        tokens.access = jwtOf("7")
        assertEquals("job-1", store().load(), "로그아웃 상태에서는 남기지 않는다")
    }

    @Test
    fun `마지막 셀카 실패는 남긴 작업과 따로 남고 다른 회원에게는 보이지 않는다(DV-성-10)`() {
        store().clear()
        store().clearFailure()
        store().saveFailure(SelfieFailure("job-1", "GENERATION_OUTCOME_UNKNOWN"))
        store().save("job-2")
        store().clear()
        assertEquals(SelfieFailure("job-1", "GENERATION_OUTCOME_UNKNOWN"), store().failure())
        store().saveFailure(SelfieFailure("job-3", null))
        assertEquals(SelfieFailure("job-3", null), store().failure())
        tokens.access = jwtOf("8")
        assertNull(store().failure())
        tokens.access = jwtOf("7")
        store().clearFailure()
        assertNull(store().failure())
    }

    private class Tokens(var access: String?) : TokenProvider {
        override fun accessToken(): String? = access

        override fun refreshToken(): String? = null

        override fun update(access: String, refresh: String) = Unit

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }
}
