@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.MyAvatar
import com.a207.runkk.app.domain.account.MySummary
import com.a207.runkk.app.domain.account.PlacementStatus
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.PlacementRoundState
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

/** 배치고사 판 결과(FR-280, D-501): 정리 중 다시 읽기, 완료 전환, 세지 않은 판, 다음 판·홈. */
class PlacementResultViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val accounts = FakeAccounts()
    private val details = RunDetailSource { Outcome.Success(recordDetail(it)) }

    private fun placementResult(
        state: PlacementRoundState?,
        verdict: String? = "WIN",
        battleId: Long = 9
    ): BattleResult =
        battleResult(verdict = verdict).copy(battleId = battleId, placementRound = 1, placementState = state)

    private fun TestScope.open(w: BattleWorld, vms: ViewModelCloser, battleId: Long = 9): PlacementResultViewModel =
        vms.track(PlacementResultViewModel(w.battles, details, accounts, w.uploads)).also {
            it.load(battleId)
            runCurrent()
        }

    private fun BattleWorld.resultCalls() = battles.calls.count { it.startsWith("result:") }

    @Test
    fun `히어로 아바타는 홈과 같은 내 아바타이고 다시 읽어도 한 번만 읽는다`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(placing(1, "GOLD"))
        accounts.summaryResult = Outcome.Success(MySummary("달리는곰", "GOLD", 3, "default:CAT", "bg-park", 12))
        val w = world()
        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.COUNTED))
        val vm = open(w, vms)
        assertEquals(MyAvatar("default:CAT", emptyList(), "bg-park"), vm.state.value.myAvatar)
        accounts.summaryResult = Outcome.Success(MySummary("달리는곰", "GOLD", 3, "default:FOX", null, 12))
        vm.load(9, isForced = true)
        runCurrent()
        assertEquals("default:CAT", vm.state.value.myAvatar?.faceKey)
    }

    @Test
    fun `TC-5880 판정 대기는 2초 간격 15번까지 다시 읽는다`() = main.runVmTest { vms ->
        // 다시 읽기 14번째에 COUNTED
        accounts.placementResult = Outcome.Success(placing(1, "GOLD"))
        val early = world()
        repeat(14) { early.battles.resultQueue += Outcome.Success(placementResult(PlacementRoundState.PENDING)) }
        early.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.COUNTED))
        val first = open(early, vms)
        assertTrue(first.state.value.isPending)
        advanceTimeBy(13 * 2_000L + 100)
        assertEquals(PlacementRoundState.PENDING, first.state.value.state)
        advanceTimeBy(2_000L)
        assertEquals(PlacementRoundState.COUNTED, first.state.value.state)
        assertEquals(15, early.resultCalls(), "처음 1 + 다시 14")
        advanceTimeBy(10_000L)
        assertEquals(15, early.resultCalls(), "COUNTED면 멈춘다")

        // 15번 모두 PENDING
        val late = world()
        late.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.PENDING))
        val stuck = open(late, vms)
        advanceTimeBy(15 * 2_000L + 100)
        assertEquals(16, late.resultCalls(), "처음 1 + 다시 15 = 30초")
        assertTrue(stuck.state.value.isStillPending)
        assertEquals(UiText.Res(R.string.placement_result_still_pending), stuck.state.value.notice)
        advanceTimeBy(10_000L)
        assertEquals(16, late.resultCalls(), "16번째 다시 읽기 없음")
    }

    @Test
    fun `TC-5880 다시 읽기 15번째에 COUNTED여도 반영한다`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(placing(1, "GOLD"))
        val w = world()
        repeat(15) { w.battles.resultQueue += Outcome.Success(placementResult(PlacementRoundState.PENDING)) }
        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.COUNTED))
        val vm = open(w, vms)
        advanceTimeBy(15 * 2_000L + 100)
        assertEquals(PlacementRoundState.COUNTED, vm.state.value.state)
        assertFalse(vm.state.value.isStillPending)
        assertEquals(16, w.resultCalls())
    }

    @Test
    fun `TC-5881 세 번째 판을 세면 완료 화면으로 간다`() = main.runVmTest { vms ->
        val events = mutableListOf<PlacementResultEvent>()
        // 마지막 판이 이 대결
        accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD"))
        val w = world()
        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.COUNTED, battleId = 903))
        val done = vms.track(PlacementResultViewModel(w.battles, details, accounts, w.uploads))
        backgroundScope.launch { done.events.collect { events += it } }
        done.load(903)
        runCurrent()
        assertEquals(
            listOf<PlacementResultEvent>(PlacementResultEvent.Replace(Routes.Key(Routes.PLACEMENT_COMPLETE))),
            events
        )

        // 마지막 판이 다른 대결: 판 결과 그대로
        events.clear()
        val other = vms.track(PlacementResultViewModel(w.battles, details, accounts, w.uploads))
        backgroundScope.launch { other.events.collect { events += it } }
        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.COUNTED, battleId = 950))
        other.load(950)
        runCurrent()
        assertEquals(emptyList(), events)

        // 진행 중: 진행 카드(n/3판 완료)
        accounts.placementResult = Outcome.Success(placing(2, "SILVER"))
        val progressing = open(w, vms, 950)
        assertEquals(2, progressing.state.value.progress?.countedRounds)
        assertEquals(PlacementRoundState.COUNTED, progressing.state.value.state)
    }

    @Test
    fun `TC-5882 세지 않은 판은 안내를 보이고 진행은 그대로다`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(placing(1, "GOLD"))
        val w = world()
        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.NOT_COUNTED, verdict = "LOSE"))
        val vm = open(w, vms)
        val state = vm.state.value
        assertEquals(1, state.progress?.countedRounds)
        assertEquals(UiText.Res(R.string.placement_result_not_counted), state.notice)
        assertEquals(Verdict.LOSE, state.verdict, "성과 첫 줄 「패배」")
        assertEquals(R.string.placement_verdict_lose, verdictLabelRes(Verdict.LOSE))
        assertEquals(1, w.resultCalls(), "다시 읽지 않는다")
    }

    /** 3판째(903) 판 결과를 연다. 진행은 완료(마지막 판 903)이고 이벤트를 [events]에 모은다. */
    private fun TestScope.openLast(
        w: BattleWorld,
        vms: ViewModelCloser,
        events: MutableList<PlacementResultEvent>
    ): PlacementResultViewModel {
        val vm = vms.track(PlacementResultViewModel(w.battles, details, accounts, w.uploads))
        backgroundScope.launch { vm.events.collect { events += it } }
        vm.load(903)
        runCurrent()
        return vm
    }

    private val complete = PlacementResultEvent.Replace(Routes.Key(Routes.PLACEMENT_COMPLETE))

    @Test
    fun `30초 뒤 정리 중이어도 기록이 올라가면 다시 읽어 3판째면 완료로 간다(PL2-A2)`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD"))
        val w = world()
        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.PENDING, battleId = 903))
        val events = mutableListOf<PlacementResultEvent>()
        val vm = openLast(w, vms, events)
        advanceTimeBy(15 * 2_000L + 100)
        assertTrue(vm.state.value.isStillPending)

        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.COUNTED, battleId = 903))
        w.pendingRuns.runs.value = listOf(pendingBattleRun(10))
        runCurrent()
        assertEquals(PlacementRoundState.COUNTED, vm.state.value.state)
        assertEquals(listOf<PlacementResultEvent>(complete), events)
    }

    @Test
    fun `화면에 돌아오면 정리 중인 판을 다시 읽고 정리가 끝난 판은 다시 읽지 않는다(PL2-A2)`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD"))
        val w = world()
        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.PENDING, battleId = 903))
        val events = mutableListOf<PlacementResultEvent>()
        val vm = openLast(w, vms, events)
        vm.onResumed()
        runCurrent()
        assertEquals(1, w.resultCalls(), "다시 읽는 중에는 겹쳐 읽지 않는다")
        advanceTimeBy(15 * 2_000L + 100)

        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.COUNTED, battleId = 903))
        vm.onResumed()
        runCurrent()
        assertEquals(listOf<PlacementResultEvent>(complete), events)
        val calls = w.resultCalls()
        vm.onResumed()
        runCurrent()
        assertEquals(calls, w.resultCalls(), "정리가 끝났으면 돌아와도 다시 읽지 않는다")
    }

    @Test
    fun `다시 확인은 보이던 결과를 가리지 않고 다시 읽는다(PL2-A2)`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD"))
        val w = world()
        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.PENDING, battleId = 903))
        val events = mutableListOf<PlacementResultEvent>()
        val vm = openLast(w, vms, events)
        advanceTimeBy(15 * 2_000L + 100)

        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.COUNTED, battleId = 903))
        vm.load(903, isForced = true)
        assertFalse(vm.state.value.isLoading, "결과를 가리지 않는다")
        assertFalse(vm.state.value.isStillPending)
        runCurrent()
        assertEquals(listOf<PlacementResultEvent>(complete), events)
    }

    @Test
    fun `진행 읽기가 끝내 실패하면 오류를 보이고 다시 시도로 완료에 닿는다(PL2-A2)`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD"))
        repeat(3) { accounts.placementQueue += Outcome.Failure(AppError.Network) }
        val w = world()
        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.COUNTED, battleId = 903))
        val events = mutableListOf<PlacementResultEvent>()
        val vm = openLast(w, vms, events)
        advanceTimeBy(2 * 2_000L + 100)
        assertEquals(UiText.Res(R.string.placement_load_failed), vm.state.value.progressError)
        assertNull(vm.state.value.progress, "앞서 읽은 진행을 남기지 않는다(한 판 적게 보이지 않게)")
        assertEquals(emptyList(), events)

        vm.load(903, isForced = true)
        runCurrent()
        assertNull(vm.state.value.progressError)
        assertEquals(listOf<PlacementResultEvent>(complete), events)
    }

    @Test
    fun `진행 읽기가 실패해 오류인 판은 화면에 돌아오면 다시 읽는다(PL2-A2)`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD"))
        repeat(3) { accounts.placementQueue += Outcome.Failure(AppError.Network) }
        val w = world()
        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.COUNTED, battleId = 903))
        val events = mutableListOf<PlacementResultEvent>()
        val vm = openLast(w, vms, events)
        advanceTimeBy(2 * 2_000L + 100)
        vm.onResumed()
        runCurrent()
        assertEquals(listOf<PlacementResultEvent>(complete), events)
    }

    @Test
    fun `모르는 판 셈 상태는 정리 중으로 다시 읽고 다시 읽는 중 완료가 오면 완료로 간다(PL2-T1)`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD"))
        val w = world()
        w.battles.resultQueue += Outcome.Success(placementResult(null, battleId = 903))
        w.battles.resultQueue += Outcome.Success(placementResult(PlacementRoundState.PENDING, battleId = 903))
        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.COUNTED, battleId = 903))
        val events = mutableListOf<PlacementResultEvent>()
        val vm = openLast(w, vms, events)
        assertTrue(vm.state.value.isPending, "null = 정리 중")
        assertEquals(emptyList(), events)
        advanceTimeBy(2 * 2_000L + 100)
        assertEquals(PlacementRoundState.COUNTED, vm.state.value.state)
        assertEquals(listOf<PlacementResultEvent>(complete), events)
    }

    @Test
    fun `TC-5883 다음 판 뛰기는 배치고사 진행으로, 뒤로는 홈으로 간다`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(placing(1, "GOLD"))
        val w = world()
        w.battles.resultResult = Outcome.Success(placementResult(PlacementRoundState.COUNTED))
        val vm = open(w, vms)
        val events = mutableListOf<PlacementResultEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        vm.onNextRound()
        vm.onHome()
        vm.onBack()
        runCurrent()
        assertEquals(
            listOf(PlacementResultEvent.NextRound, PlacementResultEvent.Home, PlacementResultEvent.Home),
            events
        )
    }
}
