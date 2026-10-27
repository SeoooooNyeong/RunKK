package com.a207.runkk.app.api.contract

import com.a207.runkk.app.domain.run.OverspeedRule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 달리는 중 거리에서 빼는 초과 시속 상한(OverspeedRule)이 서버 유효 판정 기준(ValidityRule.STANDARD.maxSpeedKmh)과 같은지 대조한다
 * (FR-047 v0.10). 서버 값은 `python android/contract/sync_from_server.py`가 contract/policy/ValidityRule.txt로 뽑는다.
 */
class ValidityRuleContractTest {
    @Test
    fun `달리는 중 빼는 초과 시속 상한은 서버 유효 판정 기준과 같다`() {
        val dir =
            File(requireNotNull(System.getProperty("runkk.contract.dir")) { "runkk.contract.dir 속성이 없다" }, "policy")
        val server = File(dir, "ValidityRule.txt").readLines().single { it.startsWith("maxSpeedKmh ") }
        assertEquals(server.substringAfter(' ').toDouble(), OverspeedRule.MAX_SEGMENT_SPEED_KMH)
    }
}
