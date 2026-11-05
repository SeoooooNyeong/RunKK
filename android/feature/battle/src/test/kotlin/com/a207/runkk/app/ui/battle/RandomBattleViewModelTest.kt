@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import app.cash.turbine.test
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.PlacementStatus
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

/** 랜덤 대결 화면(FR-285): 배치 중이면 배치고사로, 확정 티어 배지, 거리별 최고 기록, 매칭 시작. */
class RandomBattleViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeConsentSettings()
    private val accounts = FakeAccounts()

    private fun ViewModelCloser.vm(w: BattleWorld) =
        track(RandomBattleViewModel(w.battles, w.coordinator, consentOf(settings, accounts), accounts))

    @Test
    fun `TC-5886 배치 중이면 배치고사 화면으로 바꿔 연다`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(placing(1, "GOLD"))
        val placing = vms.vm(world())
        placing.events.test { assertEquals(RandomBattleEvent.Open(Routes.Key(Routes.PLACEMENT)), awaitItem()) }

        // 완료 화면을 이미 본 완료(마지막 판 903)
        accounts.placementSeen = 903
        accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD"))
        val ranked = vms.vm(world())
        runCurrent()
        assertEquals(TierDisplay.Confirmed("GOLD"), ranked.state.value.tierDisplay)
        ranked.events.test { expectNoEvents() }
    }

    @Test
    fun `배치고사를 마쳤는데 완료 화면을 아직 안 봤으면 완료 화면으로 바꿔 연다(PL3-A1)`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD"))
        listOf(null, 777L).forEach { seen ->
            accounts.placementSeen = seen
            val vm = vms.vm(world())
            vm.events.test {
                assertEquals(RandomBattleEvent.Open(Routes.Key(Routes.PLACEMENT_COMPLETE)), awaitItem(), "기억 $seen")
            }
            assertNull(vm.state.value.tierDisplay, "완료 화면으로 바꿔 열면 배지를 달지 않는다")
        }

        // 면제는 기억과 상관없이 그대로 배지
        accounts.placementSeen = null
        accounts.placementResult = Outcome.Success(settled(PlacementStatus.EXEMPT, "SILVER"))
        val exempt = vms.vm(world())
        runCurrent()
        assertEquals(TierDisplay.Confirmed("SILVER"), exempt.state.value.tierDisplay)
        exempt.events.test { expectNoEvents() }
    }

    @Test
    fun `TC-5887 고른 거리의 최고 기록을 보이고 매칭을 시작한다`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(settled(PlacementStatus.EXEMPT, "SILVER"))
        val w = world()
        w.battles.selfResult = Outcome.Success(
            RecordBattleCandidates(
                listOf(
                    RecordBattleCandidates.Candidate(
                        null,
                        "나",
                        Distance(1_000),
                        Duration.ofSeconds(292),
                        null,
                        null,
                        false
                    ),
                    RecordBattleCandidates.Candidate(null, "나", Distance(5_000), null, null, null, false)
                )
            )
        )
        val vm = vms.vm(w)
        runCurrent()
        assertEquals(Duration.ofSeconds(292), vm.state.value.bestByDistance[Distance(1_000)])
        vm.onDistanceSelected(Distance(5_000))
        assertNull(vm.state.value.best, "5km 기록 없음 → 「아직 기록이 없어요」")
        vm.events.test {
            vm.onStartTapped()
            assertEquals(RandomBattleEvent.Open(Routes.BattleQuickMatch(5_000)), awaitItem())
        }
    }
}
