package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.testing.FakePendingRunRepository
import com.a207.runkk.app.domain.testing.T0
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private fun battleRun(id: String, battleId: Long?) = CompletedRun(
    recordId = id,
    mode = RunMode.BATTLE,
    cardKind = CardKind.BATTLE_RUN,
    battleId = battleId,
    crewRunPostId = null,
    startedAt = T0,
    elapsed = Duration.ofMinutes(5),
    distance = Distance(1_000),
    route = emptyList(),
    unstable = emptyList(),
    heartRates = emptyList(),
    savedAt = T0
)

/** BattleRecordUploads(배틀 QA 5번): 이 대결 기록이 미전송 목록에 없을 때마다 알린다. */
@OptIn(ExperimentalCoroutinesApi::class)
class BattleRecordUploadsTest {
    private val pending = FakePendingRunRepository()
    private val uploads = BattleRecordUploads(pending)

    @Test
    fun `이 대결 기록이 미전송 목록에 있으면 조용하고 빠지면 알린다`() = runTest {
        val seen = mutableListOf<Unit>()
        pending.save(battleRun("other", battleId = null))
        pending.save(battleRun("mine", battleId = 9))
        backgroundScope.launch { uploads.settled(9).collect { seen += it } }
        runCurrent()
        assertEquals(0, seen.size, "아직 올라가지 않았다")
        pending.save(battleRun("next", battleId = 10))
        runCurrent()
        assertEquals(0, seen.size, "다른 기록이 바뀌어도 조용하다")
        pending.remove("mine")
        runCurrent()
        assertEquals(1, seen.size)
    }
}
