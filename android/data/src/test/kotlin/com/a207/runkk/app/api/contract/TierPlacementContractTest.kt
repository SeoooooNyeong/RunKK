package com.a207.runkk.app.api.contract

import com.a207.runkk.app.domain.battle.RandomBattleGate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 랜덤 대결 판단(RandomBattleGate)의 두 기준 값이 서버와 같은지 대조한다(H-44, D-333, D-509). 배치고사 판 수는
 * `python android/contract/sync_from_server.py`가 서버 member.PlacementProgress.ROUNDS를 contract/policy/TierPlacement.txt로
 * 뽑고(값 이름 placementRuns는 그대로), 배치 중 티어 이름은 contract/enums/Tier.txt(서버 common.Tier)에 있다. 서버 기준이 바뀌면 이 시험이 실패한다.
 */
class TierPlacementContractTest {
    private val contract =
        File(requireNotNull(System.getProperty("runkk.contract.dir")) { "runkk.contract.dir 속성이 없다" })

    @Test
    fun `TC-5865 배치고사 판 수는 서버 PlacementProgress ROUNDS와 같다`() {
        val server = File(contract, "policy/TierPlacement.txt").readLines().single { it.startsWith("placementRuns ") }
        assertEquals(server.substringAfter(' ').toInt(), RandomBattleGate.PLACEMENT_RUNS)
    }

    @Test
    fun `워치 잠금의 배치 중 티어 이름은 서버 Tier 상수에 있다`() {
        val tiers = File(contract, "enums/Tier.txt").readLines().map(String::trim)
        assertTrue(RandomBattleGate.UNRANKED in tiers, "Tier.txt에 ${RandomBattleGate.UNRANKED}가 없다: $tiers")
    }
}
