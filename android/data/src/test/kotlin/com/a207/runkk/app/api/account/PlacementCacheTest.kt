package com.a207.runkk.app.api.account

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.PlacementProgress
import com.a207.runkk.app.domain.account.PlacementStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/** 배치 진행 기기 사본(Jira 325): 서버에서 읽은 값을 그 회원 것으로 두고, 다른 회원·로그아웃이면 내주지 않으며, 무효화하면 비운다. */
class PlacementCacheTest {
    private var member: Long? = 7
    private var reply: Outcome<PlacementProgress> = Outcome.Success(progress(1))
    private var calls = 0
    private val cache = PlacementCache(fetch = {
        calls++
        reply
    }, member = { member })

    @Test
    fun `읽기 전에는 사본이 없고 refresh 뒤에는 그 값이다`() = runTest {
        assertNull(cache.read())
        assertEquals(reply, cache.refresh())
        assertEquals(progress(1), cache.read())
        assertEquals(1, calls)
    }

    @Test
    fun `실패한 refresh는 사본을 바꾸지 않는다`() = runTest {
        cache.refresh()
        reply = Outcome.Failure(AppError.Network)
        cache.refresh()
        assertEquals(progress(1), cache.read())
    }

    @Test
    fun `다른 회원이거나 로그아웃이면 내주지 않고 무효화하면 비운다`() = runTest {
        cache.refresh()
        member = 8
        assertNull(cache.read())
        member = null
        assertNull(cache.read())
        member = 7
        assertEquals(progress(1), cache.read())
        cache.invalidate()
        assertNull(cache.read())
    }

    private fun progress(counted: Int) =
        PlacementProgress(PlacementStatus.IN_PROGRESS, counted, counted + 1, "SILVER", "UNRANKED", emptyList())
}
