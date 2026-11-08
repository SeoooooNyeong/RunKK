package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 진행 알림 모델(D-258): 시험 문서 12.3.1 TC-9601~TC-9604, TC-9609(막대 끝 값). */
class ProgressNoticeTest {
    private fun spec(kind: CardKind, opponent: String? = null, target: Distance? = null): RunStartSpec = RunStartSpec(
        mode = if (kind == CardKind.FREE_RUN) RunMode.FREE else RunMode.BATTLE,
        cardKind = kind,
        recordId = "rec-1",
        todayPlan = target?.let { PlanSnapshot(it, null) },
        opponentName = opponent
    )

    @Test
    fun `TC-9601 자유런은 Free, 크루런은 CrewRun, 나머지는 상대 이름을 단 Battle이다`() {
        val p = progress(2_140, elapsedSeconds = 712)
        val cases = listOf(
            spec(CardKind.FREE_RUN) to null,
            spec(CardKind.BATTLE_RUN, "바람처럼") to "바람처럼",
            spec(CardKind.SELF_RUN, "10월 2일의 나") to "10월 2일의 나",
            spec(CardKind.CELEB_RUN, "김선교 대공") to "김선교 대공",
            spec(CardKind.CREW_RUN, "새벽런") to CREW
        )
        for ((s, name) in cases) {
            val notice = ProgressNotice.of(s, p, gapMeters = 42)
            if (name == CREW) {
                assertIs<ProgressNotice.CrewRun>(notice)
                assertEquals(null, notice.progressEnd, "크루런은 각자 거리라 막대가 없다")
            } else if (name == null) {
                assertIs<ProgressNotice.Free>(notice)
            } else {
                assertEquals(name, assertIs<ProgressNotice.Battle>(notice).opponentName, "${s.cardKind}")
            }
            assertEquals(Duration.ofSeconds(712), notice.elapsed)
            assertEquals(Distance(2_140), notice.distance)
        }
    }

    @Test
    fun `TC-9602 목표 5km 거리 2_14km는 남은 2_86km다`() {
        val state = FreeRunGoalState.of(Distance(5_000), Distance(2_140))
        assertEquals(FreeRunGoalState.Remaining(Distance(2_860)), state)
    }

    @Test
    fun `TC-9603 목표 거리 둘레 - 정확히 목표면 달성이다`() {
        assertEquals(FreeRunGoalState.Remaining(Distance(10)), FreeRunGoalState.of(Distance(5_000), Distance(4_990)))
        assertEquals(FreeRunGoalState.Reached, FreeRunGoalState.of(Distance(5_000), Distance(5_000)))
        assertEquals(FreeRunGoalState.Reached, FreeRunGoalState.of(Distance(5_000), Distance(5_010)))
    }

    @Test
    fun `TC-9604 목표 자유는 None이고 목표 3km·10km는 거리에 따라 달성·남음이다`() {
        assertEquals(FreeRunGoalState.None, FreeRunGoalState.of(null, Distance(0)))
        assertEquals(FreeRunGoalState.None, FreeRunGoalState.of(null, Distance(12_000)))
        assertEquals(FreeRunGoalState.None, FreeRunGoalState.of(Distance.ZERO, Distance(1_000)))
        assertEquals(FreeRunGoalState.Reached, FreeRunGoalState.of(Distance(3_000), Distance(3_200)))
        assertEquals(
            FreeRunGoalState.Remaining(Distance(4_000)),
            FreeRunGoalState.of(Distance(10_000), Distance(6_000))
        )
    }

    @Test
    fun `TC-9609 진행 막대 끝은 자유런 목표 거리·대결 거리이고 목표 자유는 막대가 없다`() {
        val free = ProgressNotice.of(spec(CardKind.FREE_RUN, target = Distance(5_000)), progress(2_500), null)
        assertEquals(Distance(5_000), free.progressEnd)
        val open = ProgressNotice.of(spec(CardKind.FREE_RUN), progress(2_500), null)
        assertNull(open.progressEnd)
        val battle = ProgressNotice.of(spec(CardKind.BATTLE_RUN, "바람처럼", Distance(3_000)), progress(1_000), 12)
        assertEquals(Distance(3_000), battle.progressEnd)
        assertEquals(12, assertIs<ProgressNotice.Battle>(battle).gapMeters)
    }

    @Test
    fun `자유런 알림은 정지 여부를 싣는다`() {
        val paused = ProgressNotice.of(spec(CardKind.FREE_RUN), progress(100).copy(isPaused = true), null)
        assertEquals(true, assertIs<ProgressNotice.Free>(paused).isPaused)
    }

    private companion object {
        const val CREW = "<crew>"
    }
}
