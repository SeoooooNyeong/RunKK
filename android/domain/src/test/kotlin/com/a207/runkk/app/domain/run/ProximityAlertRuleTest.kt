package com.a207.runkk.app.domain.run

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ProximityAlertRule(A-23, D-96, D-119, D-124, D-137, D-158): 시험 문서 2.2.15 TC-6341~TC-6354. */
class ProximityAlertRuleTest {
    private val rule = ProximityAlertRule

    private fun ms(millis: Long): Duration = Duration.ofMillis(millis)

    private fun alert(gap: Int, intervalMillis: Long, isAhead: Boolean = true) =
        ProximityAlert(gap, ms(intervalMillis), isAhead)

    @Test
    fun `TC-6341 근접 알림이 꺼져 있으면 null`() {
        assertNull(rule.evaluate(30, isAlertOn = false, isWatchConnected = true))
    }

    @Test
    fun `TC-6342 워치가 연결되지 않았으면 null`() {
        assertNull(rule.evaluate(30, isAlertOn = true, isWatchConnected = false))
    }

    @Test
    fun `TC-6343 격차가 없으면(대결 아님) null`() {
        assertNull(rule.evaluate(null, isAlertOn = true, isWatchConnected = true))
    }

    @Test
    fun `TC-6344 격차가 100m를 넘으면 null`() {
        listOf(101, -101, 5_000).forEach {
            assertNull(rule.evaluate(it, isAlertOn = true, isWatchConnected = true), "$it")
        }
    }

    @Test
    fun `TC-6345 켜는 경계 100m와 99m`() {
        assertEquals(alert(100, 2_000), rule.evaluate(100, true, true))
        assertEquals(alert(100, 2_000, isAhead = false), rule.evaluate(-100, true, true))
        assertEquals(alert(99, 1_980), rule.evaluate(99, true, true))
        assertEquals(alert(99, 1_980, isAhead = false), rule.evaluate(-99, true, true))
    }

    @Test
    fun `TC-6346 아래 경계 5m와 격차 0`() {
        assertEquals(ms(300), rule.evaluate(4, true, true)?.interval)
        assertEquals(ms(300), rule.evaluate(5, true, true)?.interval)
        assertEquals(ms(320), rule.evaluate(6, true, true)?.interval)
        assertEquals(alert(0, 300, isAhead = false), rule.evaluate(0, true, true))
        assertEquals(alert(3, 300, isAhead = false), rule.evaluate(-3, true, true))
    }

    @Test
    fun `TC-6347 간격은 격차에 선형이고 앞서든 뒤처지든 같다`() {
        assertEquals(
            listOf(640L, 980L, 1_320L, 1_660L),
            listOf(24, 43, 62, 81).map { rule.evaluate(it, true, true)?.interval?.toMillis() }
        )
        assertEquals(alert(43, 980, isAhead = false), rule.evaluate(-43, true, true))
        assertEquals(ms(1_110), rule.evaluate(50, true, true)?.interval)
    }

    @Test
    fun `TC-6348 켜짐·꺼짐 변화는 보낸다`() {
        assertTrue(rule.shouldSend(null, alert(50, 1_110), ms(500)))
        assertTrue(rule.shouldSend(alert(50, 1_110), null, ms(500)))
    }

    @Test
    fun `TC-6349 계속 꺼짐이거나 보낸 적 없고 다음도 없으면 보내지 않는다`() {
        assertFalse(rule.shouldSend(null, null, Duration.ofSeconds(5)))
        assertFalse(rule.shouldSend(null, null, null))
    }

    @Test
    fun `TC-6350 간격이 100ms 이상 바뀌면 보낸다`() {
        val previous = alert(50, 1_000)
        assertFalse(rule.shouldSend(previous, alert(50, 1_099), ms(500)))
        assertTrue(rule.shouldSend(previous, alert(50, 1_100), ms(500)))
        assertTrue(rule.shouldSend(previous, alert(50, 1_101), ms(500)))
        assertTrue(rule.shouldSend(previous, alert(50, 900), ms(500)))
    }

    @Test
    fun `TC-6351 같은 간격이면 2초마다 유지 신호를 보낸다`() {
        val same = alert(50, 1_000)
        assertFalse(rule.shouldSend(same, same, ms(1_999)))
        assertTrue(rule.shouldSend(same, same, ms(2_000)))
        assertTrue(rule.shouldSend(same, same, ms(2_001)))
    }

    @Test
    fun `TC-6352 이 회에 보낸 적이 없으면 다음 알림이 있을 때 보낸다`() {
        assertTrue(rule.shouldSend(alert(50, 1_110), alert(50, 1_110), null))
    }

    @Test
    fun `TC-6353 같은 입력은 순서와 무관하게 같은 결과다`() {
        val first = listOf(rule.evaluate(43, true, true), rule.evaluate(-81, true, true))
        val again = listOf(rule.evaluate(43, true, true), rule.evaluate(-81, true, true))
        assertEquals(first, again)
        val sends = listOf(rule.shouldSend(null, first[0], ms(10)), rule.shouldSend(first[0], first[1], ms(10)))
        assertEquals(
            sends,
            listOf(rule.shouldSend(null, first[0], ms(10)), rule.shouldSend(first[0], first[1], ms(10)))
        )
        assertEquals(
            0,
            ProximityAlertRule::class.java.declaredFields.count {
                it.name != "INSTANCE" && !isConstant(it)
            }
        )
    }

    @Test
    fun `TC-6354 보낸 적 없고 다음이 없으면 보내지 않는다`() {
        assertFalse(rule.shouldSend(alert(50, 1_110), null, null))
    }

    private fun isConstant(field: java.lang.reflect.Field): Boolean =
        java.lang.reflect.Modifier.isStatic(field.modifiers) && java.lang.reflect.Modifier.isFinal(field.modifiers)
}
