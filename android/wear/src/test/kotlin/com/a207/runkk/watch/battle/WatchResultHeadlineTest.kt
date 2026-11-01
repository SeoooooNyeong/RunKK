package com.a207.runkk.watch.battle

import android.app.Application
import android.content.res.Resources
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.FinishOutcome
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** WatchResultHeadline 팩토리 여섯(R73: 기록 대결은 셀프·셀럽으로 나눴다): srs FR-249 결과 머리말 표 11줄과 보류(8종, D-381, D-388, 9.2). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WatchResultHeadlineTest {
    private val res: Resources = RuntimeEnvironment.getApplication().resources

    private fun saved(meters: Long, seconds: Long = 900): FinishOutcome = FinishOutcome.Saved(
        CompletedRun(
            recordId = "r",
            mode = RunMode.FREE,
            cardKind = CardKind.FREE_RUN,
            battleId = null,
            crewRunPostId = null,
            startedAt = Instant.EPOCH,
            elapsed = Duration.ofSeconds(seconds),
            distance = Distance(meters),
            route = emptyList(),
            unstable = emptyList(),
            heartRates = emptyList(),
            savedAt = Instant.EPOCH
        )
    )

    private fun battle(verdict: String?, mine: Long?, theirs: Long?, isPending: Boolean = false, name: String? = "민준") =
        BattleResult(
            battleId = 7,
            isPending = isPending,
            verdict = verdict,
            rows = emptyList(),
            rewardPoints = null,
            isNewRecord = false,
            opponentName = name,
            myReach = mine?.let { Duration.ofSeconds(it) },
            opponentReach = theirs?.let { Duration.ofSeconds(it) }
        )

    private fun assertHeadline(expectedTitle: String, expectedSubtitle: String?, actual: WatchResultHeadline) {
        assertEquals(expectedTitle, actual.title())
        assertEquals(expectedSubtitle, actual.subtitle(res))
    }

    @Test
    fun `자유런 목표 없음으로 종료하면 FINISH이고 문구가 없다`() {
        assertHeadline("FINISH", null, WatchResultHeadline.forFree(saved(1_040), goal = null))
    }

    @Test
    fun `자유런 목표 거리에 닿은 뒤 종료하면 GOAL 목표를 달성했어요`() {
        assertHeadline("GOAL", "목표를 달성했어요", WatchResultHeadline.forFree(saved(3_000), Distance(3_000)))
    }

    @Test
    fun `자유런 목표 거리 전에 종료하면 STOP이다(저장하지 않은 종료 포함)`() {
        assertHeadline("STOP", null, WatchResultHeadline.forFree(saved(2_999), Distance(3_000)))
        assertHeadline("STOP", null, WatchResultHeadline.forFree(FinishOutcome.NotSaved, Distance(3_000)))
    }

    @Test
    fun `배틀런 이기면 WIN 상대 이름보다 거리 차 m 먼저 들어왔어요`() {
        assertHeadline(
            "WIN",
            "민준보다 12m 먼저 들어왔어요",
            WatchResultHeadline.forBattle(battle("WIN", mine = 390, theirs = null), gapMeters = 12)
        )
    }

    @Test
    fun `배틀런 지면 LOSE 상대 이름보다 시간 차 초 늦게 들어왔어요`() {
        assertHeadline(
            "LOSE",
            "민준보다 8초 늦게 들어왔어요",
            WatchResultHeadline.forBattle(battle("LOSE", mine = 398, theirs = 390), gapMeters = -30)
        )
    }

    @Test
    fun `배틀런 도착 전 종료(포기)는 STOP이다`() {
        assertHeadline(
            "STOP",
            null,
            WatchResultHeadline.forBattle(battle("LOSE", mine = null, theirs = 390), gapMeters = null)
        )
    }

    @Test
    fun `배틀런 결과가 없거나 보류면 머리말 대신 결과를 확인하고 있어요`() {
        assertHeadline("", "결과를 확인하고 있어요", WatchResultHeadline.forBattle(null, gapMeters = 3))
        assertHeadline(
            "",
            "결과를 확인하고 있어요",
            WatchResultHeadline.forBattle(battle(null, mine = 390, theirs = null, isPending = true), gapMeters = 3)
        )
    }

    @Test
    fun `셀프런 최고 기록보다 빠르면 BEST 최고 기록을 n초 단축했어요`() {
        assertHeadline("BEST", "최고 기록을 12초 단축했어요", WatchResultHeadline.forSelf(12))
    }

    @Test
    fun `셀프런 최고 기록보다 느리면 GOOD 최고 기록까지 n초 모자랐어요`() {
        assertHeadline("GOOD", "최고 기록까지 5초 모자랐어요", WatchResultHeadline.forSelf(-5))
    }

    @Test
    fun `셀프런과 셀럽런 도착 전 종료(그만두기)는 STOP이다`() {
        assertHeadline("STOP", null, WatchResultHeadline.forSelf(null))
        assertHeadline("STOP", null, WatchResultHeadline.forCeleb(null, "셀럽"))
    }

    @Test
    fun `셀럽런 셀럽 기록보다 빠르면 WIN 셀럽 표시명보다 n초 먼저 들어왔어요`() {
        assertHeadline(
            "WIN",
            "김*수보다 30초 먼저 들어왔어요",
            WatchResultHeadline.forCeleb(30, "김*수")
        )
    }

    @Test
    fun `셀럽런 셀럽 기록보다 느리면 LOSE 셀럽 표시명보다 n초 늦게 들어왔어요`() {
        assertHeadline(
            "LOSE",
            "김*수보다 4초 늦게 들어왔어요",
            WatchResultHeadline.forCeleb(-4, "김*수")
        )
    }

    @Test
    fun `배틀런 비김은 표에 없어 FINISH로 보이고 거리 차를 모르는 WIN은 문구가 없다`() {
        assertHeadline(
            "FINISH",
            null,
            WatchResultHeadline.forBattle(battle("DRAW", mine = 390, theirs = 390), gapMeters = 0)
        )
        assertNull(
            WatchResultHeadline.forBattle(battle("WIN", mine = 390, theirs = null), gapMeters = null).subtitle(res)
        )
    }

    @Test
    fun `셀프런 최고 기록과 같은 시간은 진 것이라 GOOD 최고 기록과 같았어요(W28)`() {
        assertHeadline("GOOD", "최고 기록과 같았어요", WatchResultHeadline.forSelf(0))
    }

    @Test
    fun `셀럽런 셀럽 기록과 같은 시간은 진 것이라 LOSE 셀럽 표시명보다 먼저 들어오지 못했어요(W28)`() {
        assertHeadline(
            "LOSE",
            "김*수보다 먼저 들어오지 못했어요",
            WatchResultHeadline.forCeleb(0, "김*수")
        )
    }

    @Test
    fun `서버 판정 셀프런 WIN은 BEST이고 도달 시간 차로 단축한 초를 쓴다(W27)`() {
        assertHeadline(
            "BEST",
            "최고 기록을 20초 단축했어요",
            WatchResultHeadline.forSelfVerdict(battle("WIN", mine = 880, theirs = 900))
        )
    }

    @Test
    fun `서버 판정 셀프런 LOSE는 GOOD이고 모자란 초를 쓴다(W27)`() {
        assertHeadline(
            "GOOD",
            "최고 기록까지 7초 모자랐어요",
            WatchResultHeadline.forSelfVerdict(battle("LOSE", mine = 907, theirs = 900))
        )
    }

    @Test
    fun `서버 판정 셀럽런 WIN과 LOSE는 카드의 셀럽 표시명으로 문구를 낸다(W27)`() {
        assertHeadline(
            "WIN",
            "김*수보다 30초 먼저 들어왔어요",
            WatchResultHeadline.forCelebVerdict(battle("WIN", mine = 1_470, theirs = 1_500, name = "김선수"), "김*수")
        )
        assertHeadline(
            "LOSE",
            "김*수보다 4초 늦게 들어왔어요",
            WatchResultHeadline.forCelebVerdict(battle("LOSE", mine = 1_504, theirs = 1_500, name = null), "김*수")
        )
    }

    @Test
    fun `서버 판정이 없거나 보류면 머리말 대신 결과를 확인하고 있어요(W27)`() {
        assertEquals(
            WatchResultHeadline.Pending,
            WatchResultHeadline.forSelfVerdict(null)
        )
        assertEquals(
            WatchResultHeadline.Pending,
            WatchResultHeadline.forCelebVerdict(battle(null, mine = 1_470, theirs = 1_500, isPending = true), "김*수")
        )
    }

    @Test
    fun `서버 판정에서 내가 도착하지 않았으면 STOP이고 비김은 표에 없어 FINISH다`() {
        assertEquals(
            WatchResultHeadline.Stop,
            WatchResultHeadline.forSelfVerdict(battle("LOSE", mine = null, theirs = 900))
        )
        assertEquals(
            WatchResultHeadline.Finish,
            WatchResultHeadline.forCelebVerdict(battle("DRAW", mine = 1_500, theirs = 1_500), "김*수")
        )
    }

    @Test
    fun `서버가 먼저 도착한 기록을 LOSE로 판정하면(검증 무효) 시간 차 없이 머리말만 보인다`() {
        assertHeadline(
            "GOOD",
            null,
            WatchResultHeadline.forSelfVerdict(battle("LOSE", mine = 880, theirs = 900))
        )
        assertHeadline(
            "LOSE",
            null,
            WatchResultHeadline.forCelebVerdict(battle("LOSE", mine = 1_470, theirs = 1_500), "김*수")
        )
    }
}
