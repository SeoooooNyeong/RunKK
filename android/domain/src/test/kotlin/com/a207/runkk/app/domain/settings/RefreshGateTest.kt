package com.a207.runkk.app.domain.settings

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 다시 읽기 문지기(홈 30초): 열쇠별 마지막 성공 시각과 진행 중 여부로 서버 다시 읽기를 고른다. */
class RefreshGateTest {
    private val t0 = Instant.parse("2026-11-18T00:00:00Z")
    private val gate = RefreshGate(Duration.ofSeconds(30))

    @Test
    fun `처음은 통과하고 진행 중이면 강제여도 막는다`() {
        assertTrue(gate.shouldRun("home", t0, force = false))
        assertFalse(gate.shouldRun("home", t0.plusSeconds(40), force = false), "진행 중")
        assertFalse(gate.shouldRun("home", t0.plusSeconds(40), force = true), "진행 중이면 강제도 막는다")
    }

    @Test
    fun `성공 뒤 30초 안은 막고 30초가 지나면 통과한다`() {
        gate.shouldRun("home", t0, force = false)
        gate.markDone("home", t0, ok = true)

        assertFalse(gate.shouldRun("home", t0.plusSeconds(29), force = false))
        assertTrue(gate.shouldRun("home", t0.plusSeconds(30), force = false))
    }

    @Test
    fun `강제면 간격 안이어도 통과한다`() {
        gate.shouldRun("home", t0, force = false)
        gate.markDone("home", t0, ok = true)

        assertTrue(gate.shouldRun("home", t0.plusSeconds(1), force = true))
    }

    @Test
    fun `실패는 시각을 남기지 않아 바로 다시 통과한다`() {
        gate.shouldRun("home", t0, force = false)
        gate.markDone("home", t0, ok = false)

        assertTrue(gate.shouldRun("home", t0.plusSeconds(1), force = false))
    }

    @Test
    fun `기기 시계가 뒤로 가면 간격을 막지 않는다`() {
        val gate = RefreshGate(Duration.ofSeconds(30))
        val t = Instant.parse("2026-11-18T03:00:00Z")
        assertTrue(gate.shouldRun("home", t, force = false))
        gate.markDone("home", t, ok = true)

        assertTrue(gate.shouldRun("home", t.minusSeconds(3600), force = false))
    }

    @Test
    fun `열쇠마다 따로 센다`() {
        gate.shouldRun("home", t0, force = false)
        gate.markDone("home", t0, ok = true)

        assertTrue(gate.shouldRun("events", t0.plusSeconds(1), force = false))
    }

    @Test
    fun `기본 간격은 30초다`() {
        val default = RefreshGate()
        default.shouldRun("home", t0, force = false)
        default.markDone("home", t0, ok = true)
        assertFalse(default.shouldRun("home", t0.plusSeconds(29), force = false))
        assertTrue(default.shouldRun("home", t0.plusSeconds(30), force = false))
    }
}
