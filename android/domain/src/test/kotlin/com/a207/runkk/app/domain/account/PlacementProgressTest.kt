package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 미확인 배치고사 완료 판정(D-531, PL3-A1): 배치고사 진행 화면과 랜덤 대결 화면이 같은 식을 쓴다. */
class PlacementProgressTest {
    private fun round(round: Int, battleId: Long) =
        PlacementRound(round, battleId, Distance(5_000), Distance(5_000), Duration.ofSeconds(1_650), null, "WIN")

    private fun progress(status: PlacementStatus, rounds: List<PlacementRound>) =
        PlacementProgress(status, rounds.size, null, null, "GOLD", rounds)

    private val completed = progress(PlacementStatus.COMPLETED, listOf(round(1, 901), round(3, 903), round(2, 902)))

    @Test
    fun `완료이고 마지막 판을 본 기억이 없거나 다르면 미확인이다(PL3-A1)`() {
        assertTrue(completed.isCompletionUnseen(null))
        assertTrue(completed.isCompletionUnseen(902), "마지막 판은 판 번호가 가장 큰 903")
        assertFalse(completed.isCompletionUnseen(903))
    }

    @Test
    fun `면제·배치 중·센 판이 없는 진행은 미확인이 아니다(PL3-A1)`() {
        assertFalse(progress(PlacementStatus.EXEMPT, emptyList()).isCompletionUnseen(null))
        assertFalse(progress(PlacementStatus.IN_PROGRESS, listOf(round(1, 901))).isCompletionUnseen(null))
        assertFalse(progress(PlacementStatus.COMPLETED, emptyList()).isCompletionUnseen(null))
    }
}
