package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.testing.ContractEnums
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 대결 값(거리·판정·사유 코드)을 서버 enum·표준 거리 목록과 대조한다. */
class ServerContractTest {
    private fun metersWith(use: String): Set<Long> = ContractEnums.lines("StandardDistance.txt")
        .map { it.split(' ') }
        .filter { use in it[2].split(',') }
        .map { it[1].toLong() }
        .toSet()

    @Test
    fun `랜덤 대결 거리는 서버가 받는 1·3·5·10km다`() {
        assertEquals(metersWith("RANDOM_BATTLE"), MatchUiState.DISTANCES.map { it.meters }.toSet())
        assertTrue(MatchUiState.DEFAULT_DISTANCE.meters in metersWith("RANDOM_BATTLE"))
    }

    @Test
    fun `유명인 거리 거르기는 서버 표준 거리다`() {
        val all = ContractEnums.lines("StandardDistance.txt").map { it.split(' ')[1].toLong() }.toSet()
        val filters = CELEB_FILTERS.mapNotNull { it.second }.map { it.meters }
        assertTrue(all.containsAll(filters), "서버 표준 거리에 없는 거르기 거리: ${filters - all}")
    }

    @Test
    fun `판정 이름은 서버 Verdict와 같다`() {
        assertEquals(ContractEnums.names("Verdict"), Verdict.entries.map { it.name }.toSet())
    }

    @Test
    fun `대결 오류 문구의 사유 코드는 서버 ReasonCode이거나 알려진 앱 코드다`() {
        val unknown = BattleErrorText.REJECTED.keys - ContractEnums.knownReasonCodes
        assertTrue(unknown.isEmpty(), "서버에 없는 사유 코드: $unknown")
    }
}
