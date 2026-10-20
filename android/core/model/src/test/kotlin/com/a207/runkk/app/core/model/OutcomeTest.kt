package com.a207.runkk.app.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutcomeTest {
    @Test
    fun `성공 값을 꺼내고 변환한다`() {
        val outcome: Outcome<Int> = Outcome.Success(2)
        assertEquals(Outcome.Success(4), outcome.map { it * 2 })
        assertEquals(2, outcome.getOrNull())
    }

    @Test
    fun `실패는 변환하지 않고 그대로 전달한다`() {
        val outcome: Outcome<Int> = Outcome.Failure(AppError.Network)
        assertEquals(Outcome.Failure(AppError.Network), outcome.map { it * 2 })
        assertNull(outcome.getOrNull())
    }

    @Test
    fun `거부 사유 코드를 담는다`() {
        val error = AppError.Rejected(AppError.Rejected.LOCATION_CONSENT)
        assertEquals("LOCATION_CONSENT", error.code)
    }
}
