package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.CrewRunCancelled
import com.a207.runkk.app.domain.live.CrewRunRelay
import com.a207.runkk.app.domain.live.CrewRunStart
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.run.sample
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakeGuidanceOutput
import com.a207.runkk.app.domain.testing.FakeLiveChannelProvider
import com.a207.runkk.app.domain.testing.FakeLocationPermission
import com.a207.runkk.app.domain.testing.FakeMeasurementHost
import com.a207.runkk.app.domain.testing.FakePendingRunRepository
import com.a207.runkk.app.domain.testing.FakeSettingsRepository
import com.a207.runkk.app.domain.testing.FakeUploadScheduler
import com.a207.runkk.app.domain.testing.FakeWatchGateway
import com.a207.runkk.app.domain.testing.T0
import com.a207.runkk.app.domain.testing.TestClock
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** CrewRunCoordinator(6.1.14): 대기 진입부터 크루런 한 회의 범위, 진행값 송신, 그만두기. */
@OptIn(ExperimentalCoroutinesApi::class)
class CrewRunCoordinatorTest {
    private val log = CallLog()
    private val clock = TestClock()
    private val host = FakeMeasurementHost()
    private val live = FakeLiveChannelProvider()
    private val parentJob = SupervisorJob()

    /** quit이 돌려줄 실패(F-3). null이면 성공. */
    private var quitFailure: AppError? = null

    /** quit만 쓰는 가짜 CrewRepository. 나머지 연산을 부르면 실패한다. */
    private val repository: CrewRepository =
        Proxy.newProxyInstance(CrewRepository::class.java.classLoader, arrayOf(CrewRepository::class.java)) {
                _,
                method,
                args
            ->
            check(method.name == "quit") { "unused: ${method.name}" }
            log.add("crew.quit:${args[0]}")
            quitFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
        } as CrewRepository

    @AfterTest
    fun tearDown() {
        parentJob.cancel()
    }

    private fun TestScope.setUp(): Pair<RunTracker, CrewRunCoordinator> {
        val tracker =
            RunTracker(
                FakePendingRunRepository(log),
                FakeUploadScheduler(log),
                FakeWatchGateway(),
                FakeGuidanceOutput(),
                FakeLocationPermission(),
                host,
                FakeSettingsRepository(),
                clock,
                backgroundScope
            )
        val appScope = CoroutineScope(StandardTestDispatcher(testScheduler) + parentJob)
        return tracker to CrewRunCoordinator(tracker, live, repository, clock, appScope)
    }

    private fun RunTracker.runTo(second: Long, meters: Double) {
        onLocationSample(sample(second, meters))
        clock.now = T0.plusSeconds(second)
        tick(clock.now)
    }

    @Test
    fun `모집 일시 + 3초 카운트다운 뒤 측정을 시작하고 진행값을 채널로 보낸다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onLobbyEntered(postId = 11, distance = Distance(3_000), myName = "나")
        runCurrent()
        assertEquals(CrewRunState.Lobby(11), coordinator.state.value)
        live.channel.messages.emit(CrewRunStart(11, T0.plusSeconds(10), listOf("나", "너")))
        runCurrent()
        advanceTimeBy(12_999)
        assertEquals(0, host.started)
        advanceTimeBy(2)
        assertEquals(1, host.started)
        tracker.runTo(1, 0.0)
        tracker.runTo(2, 3.0)
        runCurrent()
        val report = live.channel.sent.last()
        assertEquals(ProgressReport.Channel.CREW_RUN, report.channel)
        assertEquals(11L, report.channelId)
    }

    @Test
    fun `중계 메시지를 상태로 내보낸다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000), "나")
        runCurrent()
        val entries =
            listOf(
                CrewRunRelay.Entry("너", Distance(900), 1, CrewRunRelay.Connection.CONNECTED),
                CrewRunRelay.Entry("나", Distance(800), 2, CrewRunRelay.Connection.CONNECTED)
            )
        live.channel.messages.emit(CrewRunRelay(11, entries))
        runCurrent()
        assertEquals(entries, assertIs<CrewRunState.Running>(coordinator.state.value).entries)
    }

    @Test
    fun `그만두기 - quit 보고 뒤 endRun하고 범위를 닫는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000), "나")
        runCurrent()
        live.channel.messages.emit(CrewRunStart(11, T0, listOf("나")))
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(5, 12.0)
        runCurrent()
        coordinator.quit()
        runCurrent()
        assertEquals(listOf("crew.quit:11", "pending.save", "upload.requestUpload"), log.calls)
        assertIs<CrewRunState.Finished>(coordinator.state.value)
        assertTrue(parentJob.children.none())
    }

    @Test
    fun `취소 메시지 - 범위를 닫고 사유를 남긴다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000), "나")
        runCurrent()
        live.channel.messages.emit(CrewRunCancelled(11, "참가자 부족"))
        runCurrent()
        assertEquals(CrewRunState.Finished(11, "참가자 부족"), coordinator.state.value)
        assertTrue(parentJob.children.none())
    }

    @Test
    fun `F-1 출발 카운트다운 중 중계가 와도 Countdown을 지킨다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000), "나")
        runCurrent()
        live.channel.messages.emit(CrewRunStart(11, T0.plusSeconds(10), listOf("나", "너")))
        runCurrent()
        live.channel.messages.emit(
            CrewRunRelay(11, listOf(CrewRunRelay.Entry("너", Distance(0), 1, CrewRunRelay.Connection.CONNECTED)))
        )
        runCurrent()
        assertEquals(CrewRunState.Countdown(11, T0.plusSeconds(13)), coordinator.state.value)
    }

    @Test
    fun `F-3 그만두기 보고가 실패하면 다시 보내고 그래도 실패하면 failure로 알린다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000), "나")
        runCurrent()
        live.channel.messages.emit(CrewRunStart(11, T0, listOf("나")))
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(5, 12.0)
        runCurrent()
        quitFailure = AppError.Network
        coordinator.quit()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, log.calls.count { it == "crew.quit:11" })
        assertEquals(AppError.Network, coordinator.failure.value)
        assertIs<CrewRunState.Finished>(coordinator.state.value)
        assertEquals(1, host.stopped)
    }

    @Test
    fun `S-17 결과 화면이 확인하면 Idle로 돌아간다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000), "나")
        runCurrent()
        live.channel.messages.emit(CrewRunCancelled(11, "참가자 부족"))
        runCurrent()
        coordinator.acknowledgeFinished()
        assertEquals(CrewRunState.Idle, coordinator.state.value)
    }
}
