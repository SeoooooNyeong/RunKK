package com.a207.runkk.watch.ui

import com.a207.runkk.watch.heart.HeartRateCollector
import com.a207.runkk.watch.link.PhoneLink
import com.a207.runkk.watch.link.WearMessage
import com.a207.runkk.watch.testing.FakeHeartRateSensor
import com.a207.runkk.watch.testing.FakeNotificationPermissionGate
import com.a207.runkk.watch.testing.FakeSensorPermission
import com.a207.runkk.watch.testing.FakeWearDataLayer
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.T0
import com.a207.runkk.watch.testing.ok
import com.a207.runkk.wear.contract.EndRequestMessage
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
    private val notificationGate = FakeNotificationPermissionGate()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.link(): PhoneLink {
        val collector = HeartRateCollector(sensor, permission, backgroundScope)
        return PhoneLink(dataLayer, collector, SchedulerClock(testScheduler), backgroundScope).also { it.start() }
    }

    private fun TestScope.viewModel(link: PhoneLink = link()): WatchViewModel {
        val viewModel = WatchViewModel(link, SchedulerClock(testScheduler), notificationGate)
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
        assertFalse(state.isRunning)
        assertTrue(state.isPhoneConnected)
        assertEquals(5_000L, state.planDistanceMeters)
        assertEquals(330, state.planPaceSecondsPerKm)
    }

    @Test
    fun `러닝 중 표시 정보를 그대로 화면 상태로 옮긴다`() = runTest {
        val vm = viewModel()

        receive(display(running = true))

        val state = vm.uiState.value
        assertTrue(state.isRunning)
        assertEquals(754L, state.elapsedSeconds)
        assertEquals(2_510L, state.distanceMeters)
        assertEquals(301, state.paceSecondsPerKm)
        assertEquals(150, state.heartRateBpm)
        assertFalse(state.isLinkLost)
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
        assertEquals(WatchNotice.PhoneNotConnected, state.notice)
        assertFalse(state.isStartPending)
        assertTrue(dataLayer.sentMessages.isEmpty())
    }

    @Test
    fun `시작을 누르면 폰에 요청을 보내고 러닝 표시가 올 때까지 기다린다`() = runTest {
        val vm = viewModel()
        receive(display(running = false))

        vm.onStartTapped()
        runCurrent()
        assertTrue(vm.uiState.value.isStartPending)
        assertEquals(WearPaths.START_REQUEST, dataLayer.sentMessages.single().second)

        receive(display(running = true))

        assertFalse(vm.uiState.value.isStartPending)
        assertTrue(vm.uiState.value.isRunning)
    }

    @Test
    fun `폰이 시작하지 않으면 기다림을 일정 시간 뒤 푼다`() = runTest {
        val vm = viewModel()
        vm.onStartTapped()
        runCurrent()

        advanceTimeBy(WatchViewModel.START_PENDING_MILLIS + 1_000)
        runCurrent()

        assertFalse(vm.uiState.value.isStartPending)
    }

    @Test
    fun `폰이 보낸 안내는 기다림을 풀고 잠시 보였다가 사라진다`() = runTest {
        val vm = viewModel()
        vm.onStartTapped()
        runCurrent()

        receive(display(running = false, notice = "폰에서 러닝 화면을 여세요"))
        assertEquals(WatchNotice.FromPhone("폰에서 러닝 화면을 여세요"), vm.uiState.value.notice)
        assertFalse(vm.uiState.value.isStartPending)

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
        assertTrue(vm.uiState.value.isLinkLost)
        assertTrue(vm.uiState.value.isRunning)

        receive(display(running = true))
        assertFalse(vm.uiState.value.isLinkLost)
    }

    @Test
    fun `러닝 중 폰 노드가 사라지면 표시가 늦기 전이라도 다음 확인 때 연결 끊김을 보인다`() = runTest {
        val vm = viewModel()
        advanceTimeBy(PhoneLink.CONNECTION_POLL_MILLIS / 2)
        receive(display(running = true))

        dataLayer.nodes = emptyList()
        advanceTimeBy(PhoneLink.CONNECTION_POLL_MILLIS / 2 + 1)
        runCurrent()

        assertFalse(vm.uiState.value.isPhoneConnected)
        assertTrue(vm.uiState.value.isLinkLost)
    }

    @Test
    fun `심박 권한이 없으면 권한 요청을 보이고 권한 결과 뒤 다시 확인한다`() = runTest {
        permission.isGranted = false
        val vm = viewModel()
        receive(display(running = true))
        assertTrue(vm.uiState.value.isHeartRatePermissionNeeded)

        permission.isGranted = true
        vm.onPermissionResult()
        runCurrent()

        assertFalse(vm.uiState.value.isHeartRatePermissionNeeded)
        assertEquals(1, sensor.subscribers)
    }

    @Test
    fun `화면을 다시 만들어도 이미 지난 안내와 끊김을 새것처럼 보이지 않는다`() = runTest {
        val link = link()
        runCurrent()
        receive(display(running = true, notice = "이미 측정 중이에요"))
        advanceTimeBy(WatchViewModel.NOTICE_MILLIS * 2)

        val vm = viewModel(link)

        assertNull(vm.uiState.value.notice)
        assertTrue(vm.uiState.value.isLinkLost)
    }

    @Test
    fun `종료 버튼을 짧게 누르면 길게 눌러 종료 안내를 보이고 요청은 보내지 않는다`() = runTest {
        val vm = viewModel()
        receive(display(running = true))

        vm.onEndTapped()
        runCurrent()

        assertEquals(WatchNotice.HoldToEnd, vm.uiState.value.notice)
        assertTrue(dataLayer.sentMessages.none { it.second == WearPaths.END_REQUEST })
    }

    @Test
    fun `종료 버튼을 길게 누르면 종료 요청을 폰에 보낸다`() = runTest {
        val vm = viewModel()
        receive(display(running = true))

        vm.onEndLongPressed()
        runCurrent()

        val (node, path, bytes) = dataLayer.sentMessages.single { it.second == WearPaths.END_REQUEST }
        assertEquals("phone", node)
        assertEquals(WearPaths.END_REQUEST, path)
        assertEquals(T0, EndRequestMessage.decode(bytes).requestedAtEpochMillis)
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun `폰과 연결되지 않았으면 종료 요청 대신 폰과 연결 필요를 보인다`() = runTest {
        val vm = viewModel()
        receive(display(running = true))
        dataLayer.nodes = emptyList()

        vm.onEndLongPressed()
        runCurrent()

        assertEquals(WatchNotice.PhoneNotConnected, vm.uiState.value.notice)
    }

    @Test
    fun `폰이 대결 크루런이라 거절하면 그 안내를 그대로 보인다`() = runTest {
        val vm = viewModel()
        receive(display(running = true))

        receive(display(running = true, notice = "대결·크루런은 폰에서 끝내 주세요"))

        assertEquals(WatchNotice.FromPhone("대결·크루런은 폰에서 끝내 주세요"), vm.uiState.value.notice)
    }

    @Test
    fun `심박 수집이 처음 켜지고 알림 권한이 필요하면 한 번만 묻는다`() = runTest {
        notificationGate.needed = true
        val vm = viewModel()
        assertFalse(vm.uiState.value.isNotificationPermissionNeeded)

        receive(display(running = true))
        runCurrent()
        assertTrue(vm.uiState.value.isNotificationPermissionNeeded)

        vm.onNotificationPromptShown()
        runCurrent()
        assertFalse(vm.uiState.value.isNotificationPermissionNeeded)
        assertEquals(1, notificationGate.asked)

        // 수집이 멈췄다 다시 켜져도(거절한 뒤에도) 다시 묻지 않는다. 수집은 계속된다.
        receive(display(running = false))
        receive(display(running = true))
        runCurrent()
        assertFalse(vm.uiState.value.isNotificationPermissionNeeded)
        assertEquals(1, sensor.subscribers)
    }

    @Test
    fun `알림 권한이 이미 있거나 수집이 켜지지 않았으면 묻지 않는다`() = runTest {
        notificationGate.needed = false
        val vm = viewModel()
        receive(display(running = true))
        runCurrent()
        assertFalse(vm.uiState.value.isNotificationPermissionNeeded)

        notificationGate.needed = true
        val other = viewModel(link())
        receive(display(running = true, allowed = false))
        runCurrent()
        assertFalse(other.uiState.value.isNotificationPermissionNeeded)
    }
}
