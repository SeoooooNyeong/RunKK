package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewChange
import com.a207.runkk.app.domain.crew.CrewRunSignal
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.ui.crew.CrewWorld.Companion.crewWorld
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

/** 크루런 결과 다시 읽기 조건: 화면이 STARTED일 때만, 실시간 연결 중이면 쉬고 RESULT 신호만 따르며, 끊기면 3초부터 다시. */
class CrewRunResultRereadTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val signals = MutableSharedFlow<CrewRunSignal>(extraBufferCapacity = 16)

    private fun TestScope.resultVm(w: CrewWorld) = CrewRunResultViewModel(
        w.crews,
        w.coordinator,
        timeSource = testScheduler.timeSource,
        signals = { signals }
    ) { Outcome.Failure(AppError.Network) }

    private fun reads(w: CrewWorld) = w.crews.calls.count { it == "result:31" }

    @Test
    fun `화면이 STARTED가 아니면 처음 한 번만 읽고 시작해야 3초 다시 읽기를 시작한다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.resultState = crewRunResult.copy(status = CrewRunStatus.RUNNING)
        val vm = vms.track(resultVm(w))
        vm.load(31)
        runCurrent()
        advanceTimeBy(CrewRunResultViewModel.REREAD_MILLIS * 3)
        runCurrent()
        assertEquals(1, reads(w), "시작 전에는 주기 다시 읽기가 없다")
        vm.onStart()
        runCurrent()
        assertEquals(1, reads(w), "막 읽었으니 바로 또 읽지 않는다")
        advanceTimeBy(CrewRunResultViewModel.REREAD_MILLIS + 1)
        runCurrent()
        assertEquals(2, reads(w), "시작하면 3초 간격으로 다시 읽는다")
        advanceTimeBy(CrewRunResultViewModel.REREAD_MILLIS + 1)
        runCurrent()
        assertEquals(3, reads(w))
    }

    @Test
    fun `실시간이 연결되면 주기 다시 읽기를 쉬고 RESULT 신호로만 읽으며 끊기면 3초부터 다시 읽는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.resultState = crewRunResult.copy(status = CrewRunStatus.RUNNING)
        val vm = vms.track(resultVm(w))
        vm.load(31)
        vm.onStart()
        runCurrent()
        assertEquals(1, reads(w))
        signals.emit(CrewRunSignal.Link(isConnected = true))
        runCurrent()
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(1, reads(w), "연결 중에는 주기 다시 읽기가 없다")

        signals.emit(CrewRunSignal.Changed(CrewChange(11, 31, CrewChange.Resource.RESULT, 2)))
        runCurrent()
        assertEquals(2, reads(w), "RESULT 신호로 읽는다")
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(2, reads(w), "신호로 읽어도 주기 예약을 만들지 않는다")

        signals.emit(CrewRunSignal.Link(isConnected = false))
        runCurrent()
        assertEquals(2, reads(w), "끊긴 즉시가 아니라 3초 뒤")
        advanceTimeBy(CrewRunResultViewModel.REREAD_MILLIS + 1)
        runCurrent()
        assertEquals(3, reads(w), "끊기면 3초부터 다시")
        advanceTimeBy(CrewRunResultViewModel.REREAD_MILLIS + 1)
        runCurrent()
        assertEquals(4, reads(w), "간격 규칙을 처음부터 잇는다")
    }

    @Test
    fun `가려진 동안 끊겨도 다시 읽지 않는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.resultState = crewRunResult.copy(status = CrewRunStatus.RUNNING)
        val vm = vms.track(resultVm(w))
        vm.load(31)
        vm.onStart()
        runCurrent()
        signals.emit(CrewRunSignal.Link(isConnected = true))
        runCurrent()
        vm.onStop()
        signals.emit(CrewRunSignal.Link(isConnected = false))
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(1, reads(w))
    }

    @Test
    fun `읽는 중에 끊겨도 다음 다시 읽기는 3초부터다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.resultState = crewRunResult.copy(status = CrewRunStatus.RUNNING)
        val vm = vms.track(resultVm(w))
        vm.load(31)
        vm.onStart()
        runCurrent()
        repeat(14) {
            advanceTimeBy(CrewRunResultViewModel.REREAD_MILLIS + 1)
            runCurrent()
        }
        signals.emit(CrewRunSignal.Link(isConnected = true))
        runCurrent()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        w.crews.resultGate = gate
        signals.emit(CrewRunSignal.Changed(CrewChange(11, 31, CrewChange.Resource.RESULT, 2)))
        runCurrent()
        signals.emit(CrewRunSignal.Link(isConnected = false))
        runCurrent()
        w.crews.resultGate = null
        gate.complete(Unit)
        runCurrent()
        val before = reads(w)

        advanceTimeBy(CrewRunResultViewModel.REREAD_MILLIS + 1)
        runCurrent()

        assertEquals(before + 1, reads(w), "끊긴 뒤 첫 다시 읽기는 3초 뒤")
    }
}
