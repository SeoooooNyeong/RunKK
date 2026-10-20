package com.a207.runkk.watch.ui

import com.a207.runkk.watch.heart.HeartRateCollector
import com.a207.runkk.watch.link.PhoneLink
import com.a207.runkk.watch.link.WearMessage
import com.a207.runkk.watch.testing.FakeHeartRateSensor
import com.a207.runkk.watch.testing.FakeSensorPermission
import com.a207.runkk.watch.testing.FakeWearDataLayer
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.T0
import com.a207.runkk.watch.testing.ok
import com.a207.runkk.wear.contract.WatchDisplayMessage
import com.a207.runkk.wear.contract.WearPaths
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** WatchViewModel: 폰 표시 정보 → 화면 상태, 시작 요청·안내, 심박 빈칸 규칙, 연결 끊김 표시(FR-042, FR-183, 6.1.15). */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchViewModelTest {
    private val dataLayer = FakeWearDataLayer()
    private val sensor = FakeHeartRateSensor()
    private val permission = FakeSensorPermission(isGranted = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): WatchViewModel {
        val clock = SchedulerClock(testScheduler)
        val collector = HeartRateCollector(sensor, permission, backgroundScope)
        val link = PhoneLink(dataLayer, collector, clock, backgroundScope)
        link.start()
        val viewModel = WatchViewModel(link, clock)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun display(
        running: Boolean = true,
        allowed: Boolean = true,
        heartRateBpm: Int? = 150,
        notice: String? = null
    ) = WatchDisplayMessage(
        running = running,
        elapsedSeconds = 754,
        distanceMeters = 2_510,
        currentPaceSecondsPerKm = 301,
        heartRateBpm = heartRateBpm,
        planDistanceMeters = 5_000,
        planPaceSecondsPerKm = 330,
        heartRateCollectionAllowed = allowed,
        notice = notice
    )

    private fun TestScope.receive(message: WatchDisplayMessage) {
        dataLayer.incoming.tryEmit(WearMessage(WearPaths.WATCH_DISPLAY, message.encode()))
        runCurrent()
    }

    @Test
    fun `시작 화면은 오늘 계획과 폰 연결 상태를 보인다`() = runTest {
        val vm = viewModel()

        receive(display(running = false))

        val state = vm.uiState.value
        assertFalse(state.running)
        assertTrue(state.phoneConnected)
        assertEquals(5_000L, state.planDistanceMeters)
        assertEquals(330, state.planPaceSecondsPerKm)
    }

    @Test
    fun `러닝 중 표시 정보를 그대로 화면 상태로 옮긴다`() = runTest {
        val vm = viewModel()

        receive(display(running = true))

        val state = vm.uiState.value
        assertTrue(state.running)
        assertEquals(754L, state.elapsedSeconds)
        assertEquals(2_510L, state.distanceMeters)
        assertEquals(301, state.paceSecondsPerKm)
        assertEquals(150, state.heartRateBpm)
        assertFalse(state.linkLost)
    }

    @Test
    fun `워치가 잰 최근 심박을 폰 값보다 먼저 보인다`() = runTest {
        val vm = viewModel()
        receive(display(heartRateBpm = 150))

        sensor.emit(ok(T0 + testScheduler.currentTime, 163))
        runCurrent()

        assertEquals(163, vm.uiState.value.heartRateBpm)
    }

    @Test
    fun `심박이 30초 넘게 없으면 빈칸으로 둔다`() = runTest {
        val vm = viewModel()
        receive(display(heartRateBpm = null))
        sensor.emit(ok(T0 + testScheduler.currentTime, 163))
        runCurrent()

        advanceTimeBy(WatchViewModel.HEART_RATE_STALE_MILLIS + 1_000)
        receive(display(heartRateBpm = null))

        assertNull(vm.uiState.value.heartRateBpm)
    }

    @Test
    fun `수집 금지면 폰이 심박을 보내도 빈칸으로 둔다`() = runTest {
        val vm = viewModel()

        receive(display(allowed = false, heartRateBpm = 150))

        assertNull(vm.uiState.value.heartRateBpm)
    }

    @Test
    fun `폰과 연결되지 않은 채 시작을 누르면 폰과 연결 필요를 보인다`() = runTest {
        dataLayer.nodes = emptyList()
        val vm = viewModel()

        vm.onStartTapped()
        runCurrent()

        val state = vm.uiState.value
        assertEquals(WatchViewModel.NOT_CONNECTED_NOTICE, state.notice)
        assertFalse(state.startPending)
        assertTrue(dataLayer.sentMessages.isEmpty())
    }

    @Test
    fun `시작을 누르면 폰에 요청을 보내고 러닝 표시가 올 때까지 기다린다`() = runTest {
        val vm = viewModel()
        receive(display(running = false))

        vm.onStartTapped()
        runCurrent()
        assertTrue(vm.uiState.value.startPending)
        assertEquals(WearPaths.START_REQUEST, dataLayer.sentMessages.single().second)

        receive(display(running = true))

        assertFalse(vm.uiState.value.startPending)
        assertTrue(vm.uiState.value.running)
    }

    @Test
    fun `폰이 시작하지 않으면 기다림을 일정 시간 뒤 푼다`() = runTest {
        val vm = viewModel()
        vm.onStartTapped()
        runCurrent()

        advanceTimeBy(WatchViewModel.START_PENDING_MILLIS + 1_000)
        runCurrent()

        assertFalse(vm.uiState.value.startPending)
    }

    @Test
    fun `폰이 보낸 안내는 기다림을 풀고 잠시 보였다가 사라진다`() = runTest {
        val vm = viewModel()
        vm.onStartTapped()
        runCurrent()

        receive(display(running = false, notice = "폰에서 러닝 화면을 여세요"))
        assertEquals("폰에서 러닝 화면을 여세요", vm.uiState.value.notice)
        assertFalse(vm.uiState.value.startPending)

        advanceTimeBy(WatchViewModel.NOTICE_MILLIS + 1_000)
        runCurrent()
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun `러닝 중 표시가 끊기면 연결 끊김을 보이고 다시 오면 지운다`() = runTest {
        val vm = viewModel()
        receive(display(running = true))

        advanceTimeBy(WatchViewModel.LINK_LOST_MILLIS + 1_000)
        runCurrent()
        assertTrue(vm.uiState.value.linkLost)
        assertTrue(vm.uiState.value.running)

        receive(display(running = true))
        assertFalse(vm.uiState.value.linkLost)
    }

    @Test
    fun `러닝 중 폰 노드가 사라지면 표시가 늦기 전이라도 다음 확인 때 연결 끊김을 보인다`() = runTest {
        val vm = viewModel()
        advanceTimeBy(PhoneLink.CONNECTION_POLL_MILLIS / 2)
        receive(display(running = true))

        dataLayer.nodes = emptyList()
        advanceTimeBy(PhoneLink.CONNECTION_POLL_MILLIS / 2 + 1)
        runCurrent()

        assertFalse(vm.uiState.value.phoneConnected)
        assertTrue(vm.uiState.value.linkLost)
    }

    @Test
    fun `심박 권한이 없으면 권한 요청을 보이고 권한 결과 뒤 다시 확인한다`() = runTest {
        permission.isGranted = false
        val vm = viewModel()
        receive(display(running = true))
        assertTrue(vm.uiState.value.heartRatePermissionNeeded)

        permission.isGranted = true
        vm.onPermissionResult()
        runCurrent()

        assertFalse(vm.uiState.value.heartRatePermissionNeeded)
        assertEquals(1, sensor.subscribers)
    }
}
