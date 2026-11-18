@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.crew

import app.cash.turbine.test
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.CrewRunReturnReceipt
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewChange
import com.a207.runkk.app.domain.crew.CrewRunSignal
import com.a207.runkk.app.domain.crew.CrewRunSignals
import com.a207.runkk.app.domain.crew.CrewRunState
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.LobbyPhase
import com.a207.runkk.app.domain.crew.MyRecordUpload
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import com.a207.runkk.app.domain.live.CrewRunChanged
import com.a207.runkk.app.domain.live.CrewRunDeparture
import com.a207.runkk.app.domain.run.LocationSample
import com.a207.runkk.app.ui.crew.CrewWorld.Companion.crewWorld
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

/**
 * 크루런 실시간 반영의 앱 쪽 시험(설계 05-32 8장 앱 TC). 대기실(GET·멱등 준비·지금 시작·출발 시트·중간 참여), 크루런 탭 신호·당겨서
 * 새로고침, 레이스 재진입·강조, 결과 「확인」의 내 기록 기다림을 실제 CrewRunCoordinator(+ RunTracker)와 가짜 포트로 본다.
 */
class CrewRealtimeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val signals = MutableSharedFlow<CrewChange>(extraBufferCapacity = 16)

    private fun changed(postId: Long, resource: CrewRunChanged.Resource, revision: Long = 1, crewId: Long = 11) =
        CrewRunChanged(crewId, postId, resource, revision, "$crewId:$postId:$revision:$resource", start)

    /** 대기실 화면이 받는 변경(CrewRunSignals가 바꾼 값). */
    private fun change(postId: Long, resource: CrewChange.Resource, revision: Long = 1) =
        CrewChange(11, postId, resource, revision)

    private fun TestScope.lobbyVm(
        w: CrewWorld,
        vms: ViewModelCloser,
        key: Routes.CrewRunLobby = Routes.CrewRunLobby(31)
    ) = vms.track(CrewRunViewModel(w.crews, w.coordinator, w.clock, ServerClock(w.clock), changes = { signals }))
        .also {
            it.bind(key)
            it.onStart()
            runCurrent()
        }

    /** 내가 대기실에 들어와 준비한 대기실(새 API 서버). */
    private fun CrewWorld.readyLobby() {
        crews.lobbyApi = true
        crews.lobbyState = lobby.copy(
            participants = lobby.participants.map { if (it.isMe) it.copy(isReady = true) else it },
            phase = LobbyPhase.LOBBY
        )
    }

    // ---------- 대기실 ----------

    @Test
    fun `TC-11019 이미 준비한 대기실에 다시 들어오면 GET만 읽고 enter를 보내지 않아 준비가 풀리지 않는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.readyLobby()
        val vm = lobbyVm(w, vms)
        assertEquals(listOf("lobby:31"), w.crews.calls.filter { it.startsWith("lobby") || it.startsWith("enter") })
        assertTrue(vm.state.value.isReady)
        assertEquals(CrewRunPhase.LOBBY, vm.state.value.phase)
    }

    @Test
    fun `아직 입장하지 않은 대기실이면 GET 뒤 한 번만 enter한다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.lobbyApi = true
        w.crews.lobbyRead = lobby.copy(
            participants = lobby.participants.map { if (it.isMe) it.copy(isEntered = false) else it },
            phase = LobbyPhase.LOBBY
        )
        lobbyVm(w, vms)
        assertEquals(
            listOf("lobby:31", "enter:31"),
            w.crews.calls.filter {
                it.startsWith("lobby") ||
                    it.startsWith("enter")
            }
        )
    }

    @Test
    fun `TC-11020 이 대기실의 변경 신호에는 대기실만 다시 읽고 다른 글·다른 자원 신호에는 읽지 않는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.readyLobby()
        lobbyVm(w, vms)
        fun reads() = w.crews.calls.count { it == "lobby:31" }
        assertEquals(1, reads())
        signals.emit(change(31, CrewChange.Resource.LOBBY, 2))
        runCurrent()
        assertEquals(2, reads())
        signals.emit(change(99, CrewChange.Resource.LOBBY, 3))
        signals.emit(change(31, CrewChange.Resource.SCHEDULE, 4))
        signals.emit(change(31, CrewChange.Resource.RESULT, 5))
        runCurrent()
        assertEquals(2, reads())
        assertTrue(w.crews.calls.none { it == "race" || it.startsWith("ranking") || it.startsWith("posts") })
    }

    @Test
    fun `TC-11050 보이는 동안 60초에 스스로 다시 읽지 않고, 가려지면 신호를 받지 않으며 돌아오면 한 번 읽는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.readyLobby()
        val vm = lobbyVm(w, vms)
        fun reads() = w.crews.calls.count { it == "lobby:31" }
        advanceTimeBy(60_000)
        assertEquals(1, reads(), "주기 읽기가 없다")
        vm.onStop()
        signals.emit(change(31, CrewChange.Resource.LOBBY, 2))
        advanceTimeBy(60_000)
        assertEquals(1, reads(), "가려진 동안에는 읽지 않는다")
        vm.onStart()
        runCurrent()
        assertEquals(2, reads(), "다시 보이면 한 번 읽는다(재진입)")
        vm.onPullRefresh()
        runCurrent()
        assertEquals(3, reads(), "당겨서 새로고침")
        assertFalse(vm.state.value.isRefreshing)
    }

    @Test
    fun `준비는 바랄 값을 멱등 PUT으로 보낸다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.readyLobby()
        val vm = lobbyVm(w, vms)
        vm.onReadyToggle()
        runCurrent()
        assertEquals(
            listOf("setReady:31:false"),
            w.crews.calls.filter {
                it.startsWith("setReady") ||
                    it.startsWith("ready")
            }
        )
        assertFalse(vm.state.value.isReady)
    }

    @Test
    fun `TC-11025 모집자는 canStartNow일 때만 지금 시작을 보내고 고정 후보면 카운트다운을 본다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.readyLobby()
        val vm = lobbyVm(w, vms)
        vm.onStartNow()
        assertTrue(w.crews.calls.none { it.startsWith("startNow") }, "canStartNow가 아니면 보내지 않는다")
        w.crews.lobbyState = w.crews.lobbyState.copy(canStartNow = true)
        vm.onPullRefresh()
        runCurrent()
        assertTrue(vm.state.value.canStartNow)
        w.crews.lobbyState = w.crews.lobbyState.copy(
            canStartNow = false,
            phase = LobbyPhase.COUNTDOWN,
            startAt = w.clock.now.plusSeconds(3),
            countdownParticipantIds = listOf(1, 7)
        )
        vm.onStartNow()
        runCurrent()
        assertEquals(listOf("startNow:31"), w.crews.calls.filter { it.startsWith("startNow") })
        assertEquals(CrewRunPhase.COUNTDOWN, vm.state.value.phase)
        assertEquals(3L, vm.state.value.countdown, "startAt까지 3초, 다시 3초를 더하지 않는다")
    }

    @Test
    fun `TC-11024 카운트다운 중 준비 취소하고 기다리면 후보에서 빠져 대기실로 돌아가고 측정하지 않는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.readyLobby()
        w.crews.lobbyState = w.crews.lobbyState.copy(
            phase = LobbyPhase.COUNTDOWN,
            startAt = w.clock.now.plusSeconds(3),
            countdownParticipantIds = listOf(1, 7)
        )
        val vm = lobbyVm(w, vms)
        assertEquals(CrewRunPhase.COUNTDOWN, vm.state.value.phase)
        w.crews.lobbyState = w.crews.lobbyState.copy(countdownParticipantIds = listOf(1))
        vm.onReadyToggle()
        runCurrent()
        assertEquals(listOf("setReady:31:false"), w.crews.calls.filter { it.startsWith("setReady") })
        assertEquals(CrewRunPhase.LOBBY, vm.state.value.phase)
        assertIs<CrewRunState.Lobby>(w.coordinator.state.value)
    }

    @Test
    fun `TC-11027 준비하지 않은 입장자는 출발 사실에 시트를 보고 나가기는 서버에 알리지 않고 닫는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.readyLobby()
        val vm = lobbyVm(w, vms)
        w.crews.lobbyRead =
            w.crews.lobbyState.copy(phase = LobbyPhase.RUNNING, canJoin = true, countdownParticipantIds = listOf(1, 2))
        w.channels.channel.push(CrewRunDeparture(11, 31, 9, "31-8", w.clock.now, listOf(1, 2)))
        runCurrent()
        assertEquals(CrewRunPhase.JOIN_CHOICE, vm.state.value.phase)
        assertEquals(2, vm.state.value.departedCount)
        assertEquals(0, w.tracker.progress.value.elapsed.seconds.toInt(), "자동 측정하지 않는다")
        vm.events.test {
            vm.onJoinDismissed()
            runCurrent()
            assertEquals(CrewRunEvent.Left, awaitItem())
        }
        assertTrue(w.crews.calls.none { it.startsWith("leaveLobby") || it.startsWith("join") })
    }

    @Test
    fun `TC-11028 함께 뛰기는 승인 시각부터 재고 중간 참여 표시 없이 진행 화면이 된다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.readyLobby()
        val vm = lobbyVm(w, vms)
        w.crews.lobbyRead = w.crews.lobbyState.copy(phase = LobbyPhase.RUNNING, canJoin = true)
        w.channels.channel.push(CrewRunDeparture(11, 31, 9, "31-8", w.clock.now, listOf(1)))
        runCurrent()
        w.clock.now = w.clock.now.plusSeconds(300)
        w.crews.joinState = w.crews.joinState.copy(joinedAt = w.clock.now)
        vm.onJoin()
        runCurrent()
        assertEquals(listOf("join:31"), w.crews.calls.filter { it.startsWith("join") })
        assertEquals(CrewRunPhase.RUNNING, vm.state.value.phase)
        assertFalse(vm.state.value.isJoining)
    }

    @Test
    fun `TC-11028 크루런 탭 중간 참여하기로 오면 묻지 않고 바로 참여하고 enter를 보내지 않는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.lobbyApi = true
        w.crews.lobbyState = lobby.copy(phase = LobbyPhase.RUNNING, canJoin = true)
        val vm = lobbyVm(w, vms, Routes.CrewRunLobby(31, join = true))
        assertEquals(
            listOf("lobby:31", "join:31"),
            w.crews.calls.filter {
                it.startsWith("lobby") ||
                    it.startsWith("join") ||
                    it.startsWith("enter")
            }
        )
        assertEquals(CrewRunPhase.RUNNING, vm.state.value.phase)
    }

    @Test
    fun `참여가 거부되면(종료 뒤) 사유를 보이고 측정하지 않는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.lobbyApi = true
        w.crews.lobbyState = lobby.copy(phase = LobbyPhase.RUNNING, canJoin = true)
        w.crews.fail["join"] = AppError.Rejected("INVALID_STATE")
        val vm = lobbyVm(w, vms)
        assertEquals(CrewRunPhase.JOIN_CHOICE, vm.state.value.phase)
        vm.onJoin()
        runCurrent()
        assertEquals(CrewRunPhase.JOIN_CHOICE, vm.state.value.phase)
        assertFalse(vm.state.value.isJoining)
        assertTrue(vm.state.value.error != null)
    }

    // ---------- 크루런 탭·레이스 ----------

    private fun TestScope.homeVm(vms: ViewModelCloser, repo: FakeCrewRepository, live: FakeLiveChannels) = vms.track(
        CrewHomeViewModel(
            repo,
            ServerClock(MutableClock()),
            FakeCelebrationSharer(),
            { _, _ -> Outcome.Success(Unit) },
            signals = CrewRunSignals(live)
        )
    ).also {
        it.bind(Routes.CrewHome(11))
        runCurrent()
    }

    @Test
    fun `TC-11031 크루런 탭이 열려 있으면 목록 변경 신호에 읽은 범위를 다시 읽고 레이스·다른 크루 신호에는 읽지 않는다`() = main.runVmTest { vms ->
        val repo = FakeCrewRepository()
        val live = FakeLiveChannels()
        val vm = homeVm(vms, repo, live)
        fun lists() = repo.calls.count { it == "posts:CREW_RUN" }
        live.channel.push(changed(31, CrewRunChanged.Resource.SCHEDULE))
        runCurrent()
        assertEquals(0, lists(), "레이스 탭이 보일 때는 목록을 읽지 않는다")
        vm.onTab(CrewTab.SCHEDULE)
        runCurrent()
        assertEquals(1, lists())
        live.channel.push(changed(31, CrewRunChanged.Resource.SCHEDULE, 2))
        runCurrent()
        assertEquals(2, lists())
        live.channel.push(changed(31, CrewRunChanged.Resource.SCHEDULE, 3, crewId = 99))
        live.channel.push(changed(31, CrewRunChanged.Resource.RESULT, 4))
        runCurrent()
        assertEquals(2, lists())
        vm.onPaused()
        live.channel.push(changed(31, CrewRunChanged.Resource.SCHEDULE, 5))
        runCurrent()
        assertEquals(2, lists(), "화면이 멈추면 신호를 받지 않는다")
        assertTrue(live.channel.closed > 0, "임대를 반납한다")
    }

    @Test
    fun `TC-11033 크루런 탭은 날짜순이고 같은 일시는 글 ID순이며 같은 글은 한 번만 보인다`() {
        val at = crewRunVoting.scheduledAt
        fun post(id: Long, plus: Long) = crewRunPosts.posts[0].copy(
            postId = id,
            crewRun = crewRunVoting.copy(scheduledAt = at.plusSeconds(plus))
        )
        val state = CrewHomeUiState(crewRuns = PostList(listOf(post(40, 60), post(33, 0), post(31, 0), post(33, 0))))
        assertEquals(listOf(31L, 33L, 40L), upcomingSchedules(state).map { it.postId })
    }

    @Test
    fun `TC-11034 당겨서 새로고침이 실패하면 지금 목록을 두고 안내한다`() = main.runVmTest { vms ->
        val repo = FakeCrewRepository()
        val vm = homeVm(vms, repo, FakeLiveChannels())
        vm.onTab(CrewTab.SCHEDULE)
        runCurrent()
        val before = vm.state.value.crewRuns
        repo.fail["posts"] = AppError.Network
        vm.onPullRefresh()
        runCurrent()
        assertEquals(before, vm.state.value.crewRuns)
        assertNull(vm.state.value.error, "오류 화면으로 바꾸지 않는다")
        assertTrue(vm.state.value.message != null)
        assertFalse(vm.state.value.isRefreshing)
    }

    @Test
    fun `TC-11017 결과 복귀 뒤 레이스 탭에 들어오면 이미 읽은 레이스·랭킹도 다시 읽는다`() = main.runVmTest { vms ->
        val repo = FakeCrewRepository()
        val vm = homeVm(vms, repo, FakeLiveChannels())
        assertEquals(1, repo.calls.count { it == "race" })
        vm.onTab(CrewTab.SCHEDULE)
        vm.onResumed(receipt(800))
        vm.onTab(CrewTab.RACE)
        runCurrent()
        assertEquals(2, repo.calls.count { it == "race" })
        assertEquals(2, repo.calls.count { it == "ranking:WEEKLY" })
    }

    @Test
    fun `TC-11036·11037 레이스는 직전 조회보다 늘어난 확정 거리를 2초 강조하고 같은 스냅숏은 다시 강조하지 않는다`() = main.runVmTest { vms ->
        val repo = FakeCrewRepository()
        val mine = RaceStatus.Contributor("나", Distance(12_000), 0.2, memberId = 7, isMe = true)
        val mate = RaceStatus.Contributor("한강달림", Distance(18_290), 0.3, memberId = 8)
        repo.raceState = RaceStatus(Distance(46_200), start, listOf(mate, mine), 0.26)
        val vm = homeVm(vms, repo, FakeLiveChannels())
        assertNull(vm.state.value.raceHighlight, "처음 읽은 값은 강조하지 않는다")
        // 내 0.80km가 먼저 확정됐다(한강달림은 아직).
        repo.raceState = RaceStatus(Distance(47_000), start, listOf(mate, mine.copy(distance = Distance(12_800))), 0.27)
        vm.onPullRefresh()
        runCurrent()
        val first = vm.state.value.raceHighlight
        assertEquals(true, first?.isGoal)
        assertEquals(mapOf("m:7" to Distance(800)), first?.added, "확정된 내 거리만 늘었다")
        advanceTimeBy(1_999)
        assertTrue(vm.state.value.raceHighlight != null, "1,999ms에는 아직 강조")
        advanceTimeBy(1)
        runCurrent()
        assertNull(vm.state.value.raceHighlight, "2,000ms에 끝난다")
        vm.onPullRefresh()
        runCurrent()
        assertNull(vm.state.value.raceHighlight, "같은 스냅숏은 다시 강조하지 않는다")
        // 늦게 도착한 한강달림 0.84km는 다음 읽기에서 더해지고 강조된다.
        repo.raceState =
            RaceStatus(
                Distance(47_840),
                start,
                listOf(mate.copy(distance = Distance(19_130)), mine.copy(distance = Distance(12_800))),
                0.27
            )
        vm.onPullRefresh()
        runCurrent()
        assertEquals(mapOf("m:8" to Distance(840)), vm.state.value.raceHighlight?.added)
    }

    @Test
    fun `TC-11037 직전 값이 없는 레이스는 결과 복귀일 때만 목표와 내 줄을 강조하고 증가량은 추정하지 않는다`() {
        val latest = RaceStatus(
            Distance(1_000),
            start,
            listOf(RaceStatus.Contributor("나", Distance(1_000), 1.0, memberId = 7, isMe = true)),
            1.0
        )
        assertNull(raceHighlightOf(null, latest, hasReturn = false))
        assertEquals(RaceHighlight(isGoal = true, rows = setOf("m:7")), raceHighlightOf(null, latest, hasReturn = true))
    }

    @Test
    fun `TC-11038 열어 둔 레이스는 결과·기여 신호로 다시 읽지 않고 재진입·당기기만 읽는다`() = main.runVmTest { vms ->
        val repo = FakeCrewRepository()
        val live = FakeLiveChannels()
        homeVm(vms, repo, live)
        live.channel.push(changed(31, CrewRunChanged.Resource.RESULT))
        live.channel.push(changed(31, CrewRunChanged.Resource.PROGRESS))
        live.channel.push(changed(31, CrewRunChanged.Resource.SCHEDULE))
        advanceTimeBy(60_000)
        assertEquals(1, repo.calls.count { it == "race" })
    }

    // ---------- 결과 「확인」 ----------

    private fun receipt(meters: Long) = CrewRunReturnReceipt("rec", 31, "rec", meters, start)

    private fun TestScope.resultVm(
        w: CrewWorld,
        uploads: MutableStateFlow<MyRecordUpload>,
        retried: MutableList<String>
    ) = CrewRunResultViewModel(
        w.crews,
        w.coordinator,
        timeSource = testScheduler.timeSource,
        observeUpload = { _, _ -> uploads },
        retryUpload = { retried += it },
        now = { start }
    ) { Outcome.Failure(AppError.Network) }

    /** 내가 달리고 끝낸 크루런(조정자 Finished에 내 기록 ID가 남는다). */
    private fun TestScope.finishMyRun(w: CrewWorld): String {
        w.crews.lobbyApi = true
        w.crews.lobbyState = lobby.copy(phase = LobbyPhase.RUNNING, isMeRunner = true)
        w.crews.joinState = w.crews.joinState.copy(joinedAt = w.clock.now)
        w.coordinator.onLobbyEntered(31, Distance(5_000))
        w.coordinator.onLobby(w.crews.lobbyState)
        runCurrent()
        // 5초마다 15m씩(시속 10.8km) 1분을 달린다.
        (0..12).forEach { step ->
            w.tracker.onLocationSample(LocationSample(w.clock.now, GeoPoint(37.5 + step * NORTH_15M, 127.0), 5.0))
            w.tracker.tick(w.clock.now)
            w.clock.now = w.clock.now.plusSeconds(5)
        }
        w.coordinator.quit()
        runCurrent()
        return assertIs<CrewRunState.Finished>(w.coordinator.state.value).recordId!!
    }

    @Test
    fun `TC-11016 확인은 내 기록이 저장될 때까지 기다리고 저장되면 바로 복귀 값과 함께 나간다(타인 달리기와 무관)`() = main.runVmTest { vms ->
        val w = crewWorld()
        val recordId = finishMyRun(w)
        w.crews.resultState = crewRunResult.copy(status = CrewRunStatus.RUNNING, myRecordId = null)
        val uploads = MutableStateFlow<MyRecordUpload>(MyRecordUpload.Waiting)
        val vm = vms.track(resultVm(w, uploads, mutableListOf()))
        vm.load(31)
        runCurrent()
        vm.events.test {
            vm.onConfirm()
            runCurrent()
            assertEquals(ConfirmState.WAITING, vm.state.value.confirm)
            expectNoEvents()
            uploads.value = MyRecordUpload.Stored(isValid = true, distance = Distance(800))
            runCurrent()
            val leave = assertIs<CrewRunResultEvent.Leave>(awaitItem())
            assertEquals(CrewRunReturnReceipt(recordId, 31, recordId, 800, start), leave.receipt)
        }
    }

    @Test
    fun `TC-11044 valid=false면 유효 거리 0으로 나가고, 내 전송 실패는 다시 올리기, 거부는 안내 뒤 복귀 값 없이 나간다`() = main.runVmTest { vms ->
        val w = crewWorld()
        finishMyRun(w)
        val uploads = MutableStateFlow<MyRecordUpload>(MyRecordUpload.Retryable(AppError.Network))
        val retried = mutableListOf<String>()
        val vm = vms.track(resultVm(w, uploads, retried))
        vm.load(31)
        runCurrent()
        vm.events.test {
            vm.onConfirm()
            runCurrent()
            assertEquals(ConfirmState.RETRYABLE, vm.state.value.confirm)
            vm.onConfirm()
            runCurrent()
            assertEquals(1, retried.size, "다시 올리기")
            uploads.value = MyRecordUpload.Blocked
            runCurrent()
            assertEquals(ConfirmState.BLOCKED, vm.state.value.confirm)
            expectNoEvents()
            vm.onConfirm()
            assertEquals(CrewRunResultEvent.Leave(null), awaitItem())
        }
        val valid = MutableStateFlow<MyRecordUpload>(MyRecordUpload.Stored(isValid = false, distance = Distance(800)))
        val w2 = crewWorld()
        finishMyRun(w2)
        val vm2 = vms.track(resultVm(w2, valid, mutableListOf()))
        vm2.load(31)
        runCurrent()
        vm2.events.test {
            vm2.onConfirm()
            runCurrent()
            assertEquals(0L, assertIs<CrewRunResultEvent.Leave>(awaitItem()).receipt?.ownDistanceMeters)
        }
    }

    @Test
    fun `기록이 없거나 지난 결과를 연 확인은 기다리지 않고 복귀 값 없이 나간다`() = main.runVmTest { vms ->
        val w = crewWorld()
        val vm = vms.track(resultVm(w, MutableStateFlow(MyRecordUpload.Waiting), mutableListOf()))
        vm.load(31)
        runCurrent()
        vm.events.test {
            vm.onConfirm()
            assertEquals(CrewRunResultEvent.Leave(null), awaitItem())
        }
    }

    @Test
    fun `앱을 다시 켜 상태를 모르면 결과 응답이 내 기록을 이었을 때 저장된 것으로 본다`() = main.runVmTest { vms ->
        val w = crewWorld()
        val recordId = finishMyRun(w)
        w.crews.resultState = crewRunResult.copy(myRecordId = recordId)
        // 이어진 기록 ID를 화면이 관찰에 넘기는지 본다(판정 자체는 CrewRunCompletionTest).
        val vm = vms.track(
            CrewRunResultViewModel(
                w.crews,
                w.coordinator,
                timeSource = testScheduler.timeSource,
                observeUpload = { id, linked ->
                    linked.map { if (it == id) MyRecordUpload.Stored(true, null) else MyRecordUpload.Waiting }
                },
                now = { start }
            ) { Outcome.Failure(AppError.Network) }
        )
        vm.load(31)
        runCurrent()
        vm.events.test {
            vm.onConfirm()
            runCurrent()
            assertEquals(4_870L, assertIs<CrewRunResultEvent.Leave>(awaitItem()).receipt?.ownDistanceMeters)
        }
    }

    // ---------- 리뷰 결함 ----------

    @Test
    fun `리뷰1 끝났거나 취소된 크루런 대기실을 열면 조정자 회를 열지 않는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.lobbyApi = true
        w.crews.lobbyState = lobby.copy(phase = LobbyPhase.CANCELLED)
        lobbyVm(w, vms)
        assertEquals(CrewRunState.Idle, w.coordinator.state.value)
        assertEquals(0, w.channels.opened, "/ws 임대를 빌리지 않는다")
    }

    @Test
    fun `리뷰1 열린 대기실이 다시 읽어 취소로 바뀌면 조정자 회를 닫는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.readyLobby()
        val vm = lobbyVm(w, vms)
        assertIs<CrewRunState.Lobby>(w.coordinator.state.value)
        w.crews.lobbyState = w.crews.lobbyState.copy(phase = LobbyPhase.CANCELLED)
        vm.onPullRefresh()
        runCurrent()
        assertEquals(CrewRunPhase.CANCELLED, vm.state.value.phase)
        assertEquals(CrewRunState.Idle, w.coordinator.state.value)
        assertTrue(w.channels.channel.closed > 0)
    }

    @Test
    fun `리뷰2 GET 대기실이 404·405면 옛 서버로 보고 enter로 들어가고 PUT 준비가 NOT_FOUND면 토글한다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.lobbyApi = true
        w.crews.fail["lobby"] = AppError.Rejected("HTTP_405")
        w.crews.fail["setReady"] = AppError.Rejected("NOT_FOUND")
        val vm = lobbyVm(w, vms)
        assertTrue("enter:31" in w.crews.calls, w.crews.calls.toString())
        assertEquals(CrewRunPhase.LOBBY, vm.state.value.phase)
        vm.onReadyToggle()
        runCurrent()
        assertTrue("ready:31" in w.crews.calls, "옛 토글: ${w.crews.calls}")
    }

    @Test
    fun `리뷰3 결과를 읽는 중 온 결과 신호는 겹쳐 읽지 않고 끝난 뒤 한 번만 더 읽는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.resultState = crewRunResult.copy(status = CrewRunStatus.RUNNING)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        w.crews.resultGate = gate
        val vm = vms.track(
            CrewRunResultViewModel(
                w.crews,
                w.coordinator,
                timeSource = testScheduler.timeSource,
                signals = { signals.map { CrewRunSignal.Changed(it) } },
                now = { start }
            ) { Outcome.Failure(AppError.Network) }
        )
        vm.load(31)
        vm.onStart()
        runCurrent()
        fun reads() = w.crews.calls.count { it == "result:31" }
        assertEquals(1, reads())
        signals.emit(change(31, CrewChange.Resource.RESULT, 2))
        signals.emit(change(31, CrewChange.Resource.RESULT, 3))
        runCurrent()
        assertEquals(1, reads(), "읽는 중에는 겹쳐 보내지 않는다")
        w.crews.resultGate = null
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, reads(), "후속 한 번")
        advanceTimeBy(CrewRunResultViewModel.REREAD_MILLIS + 1)
        assertEquals(3, reads(), "기존 3초 다시 읽기는 그대로")
    }
}

/** 북쪽 15m(위도 차). */
private const val NORTH_15M = 0.000135
