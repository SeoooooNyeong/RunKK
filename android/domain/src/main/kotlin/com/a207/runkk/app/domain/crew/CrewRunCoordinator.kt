package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.CountdownStarted
import com.a207.runkk.app.domain.live.CrewChatPosted
import com.a207.runkk.app.domain.live.CrewChatReactionChanged
import com.a207.runkk.app.domain.live.CrewChatRemoved
import com.a207.runkk.app.domain.live.CrewRunCancelled
import com.a207.runkk.app.domain.live.CrewRunChanged
import com.a207.runkk.app.domain.live.CrewRunDeparture
import com.a207.runkk.app.domain.live.CrewRunEnded
import com.a207.runkk.app.domain.live.CrewRunRelay
import com.a207.runkk.app.domain.live.CrewRunStart
import com.a207.runkk.app.domain.live.FriendInviteDeclined
import com.a207.runkk.app.domain.live.FriendInviteExpired
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.MatchFailed
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import com.a207.runkk.app.domain.run.CoordinatedRound
import com.a207.runkk.app.domain.run.FinishOutcome
import com.a207.runkk.app.domain.run.RunProgress
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 크루런 러닝의 측정과 중계 메시지를 잇는다(6.1.14, 합산 방식 S15P31A207-236). 크루런 대기 진입(onLobbyEntered) 때
 * applicationScope 아래에 자기 범위를 만들고 endRun·quit·취소 뒤 닫는다(5.1.2). 상태 변경은 그 범위의 직렬 디스패처에서만 돈다.
 * 각자 개인 러닝처럼 측정하고(목표 거리에 닿아도 끝나지 않는다) 일시정지할 수 있다. 정지로 바뀌면 서버에 알리고, 재개는 서버가
 * 늘어난 경과의 진행값으로 판단한다. 출발 카운트다운(Countdown) 중에 온 중계는 그 상태를 덮지 않는다(F-1). 끝내기 보고를 다시
 * 보내도 실패하거나 채널에 연결하지 못하면 failure로 알린다(F-3, O-6).
 *
 * 출발(설계 05-32 2.1, [임시:D-성-881]~[임시:D-성-885]): 대기실 응답(onLobby)이 COUNTDOWN이고 내가 고정 후보면 Countdown(startAt)을
 * 보인다. 출발 사실(CrewRunDeparture, 옛 CrewRunStart도 같다)을 받으면 대기실을 다시 읽어 RUNNING·meRunner로 확인하고, 진행 참가자면
 * 참여(POST join, 멱등)로 내 시작 절대시각(joinedAt)과 측정 기록 ID를 받아 그 시각부터 한 번만 잰다. 앱은 3초를 더하지 않는다.
 * 진행 참가자가 아니고 참여할 수 있으면 JoinChoice(「함께 뛰기」·「나가기」)다. 같은 측정 기록 ID는 다시 시작하지 않는다.
 */
@Singleton
class CrewRunCoordinator
@Inject
constructor(
    private val runTracker: RunTracker,
    private val liveChannelProvider: LiveChannelProvider,
    private val crewRepository: CrewRepository,
    private val clock: Clock,
    private val applicationScope: CoroutineScope
) {
    private val _state = MutableStateFlow<CrewRunState>(CrewRunState.Idle)
    val state: StateFlow<CrewRunState> = _state.asStateFlow()

    private val _failure = MutableStateFlow<AppError?>(null)

    /** 이 회에서 생긴 실패(그만두기 보고 실패, 채널 연결 실패, 측정 시작 거부). 새 회·acknowledgeFinished 때 지운다. */
    val failure: StateFlow<AppError?> = _failure.asStateFlow()

    @Volatile
    private var run: CrewRun? = null

    /** 이 앱 실행에서 측정을 시작한 측정 기록 ID. 같은 측정·기록 ID로 다시 시작하지 않는다(설계 05-32 2.3). */
    private val startedRecordIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /**
     * 크루런 대기실 진입(ui.crew가 대기실을 읽은 뒤 부른다). 채널을 열고 중계를 받는다. 같은 크루런 회가 이미 열려 있으면(화면을 다시
     * 열었다) 그대로 둔다: 측정 중인 회를 닫지 않는다.
     */
    fun onLobbyEntered(postId: Long, distance: Distance?) {
        if (run?.postId == postId) return
        val run = openRun(postId, distance)
        run.round.scope.launch {
            _state.value = CrewRunState.Lobby(postId)
            val channel = liveChannelProvider.open()
            run.attach(channel)
            // 연결 직후 오는 메시지(BattleStart 등)를 잃지 않게 연결 전에 구독한다.
            run.round.scope.launch(start = CoroutineStart.UNDISPATCHED) { channel.messages.collect { handle(run, it) } }
            val connected = channel.connect()
            if (connected is Outcome.Failure) fail(run, connected.error)
        }
    }

    /**
     * 화면이 읽은 대기실(GET·PUT 준비·입장·지금 시작의 응답)을 반영한다(설계 05-32 2.1). COUNTDOWN이고 내가 고정 후보면 Countdown,
     * RUNNING이고 진행 참가자면 내 측정 시작, 참여할 수 있으면 JoinChoice, 대기실이면 Lobby로 돌린다. 이미 측정을 시작했으면 바꾸지 않는다.
     */
    fun onLobby(lobby: Lobby) {
        val run = run?.takeIf { it.postId == lobby.postId } ?: return
        run.round.scope.launch { apply(run, lobby) }
    }

    /** JoinChoice의 「함께 뛰기」·크루런 탭 「중간 참여하기」(POST join, 설계 05-32 2.1). 승인 시각부터 잰다. 실패하면 failure로 알린다. */
    fun join() {
        val run = run ?: return
        _failure.value = null
        run.round.scope.launch { joinAndStart(run) }
    }

    /**
     * JoinChoice의 「나가기」(설계 05-32 [임시:D-성-883]): 서버에 알리지 않고 측정 전 회를 닫아 Idle로 돌린다. 측정을 시작했으면 닫지
     * 않는다.
     */
    suspend fun dismiss(postId: Long) {
        val current = run?.takeIf { it.postId == postId } ?: return
        closeInRound(current)
    }

    /**
     * 진행값 한 개를 처리한다. 운영 코드는 RunTracker.progress 구독(CoordinatedRound)만 이 경로를 쓰고 화면은 부르지 않는다.
     * 시험이 늦게 처리된 진행값을 넣으려고 모듈 안(internal)에만 연다(1차 S-16). 같은 값은 한 번만 처리한다.
     */
    internal fun onProgress(p: RunProgress) {
        val run = run ?: return
        run.round.scope.launch { progress(run, p) }
    }

    fun onMessage(m: ServerMessage) {
        val run = run ?: return
        run.round.scope.launch { handle(run, m) }
    }

    /**
     * 확인 뒤 내 크루런 끝내기(POST /crew-runs/{id}/quit, F-15d) → 기록 저장. 끝낼 때의 내 거리·경과를 실어 보낸다.
     * 보고가 끝내 실패하면 failure로 알린다.
     */
    fun quit() {
        val run = run ?: return
        run.round.scope.launch {
            val last = run.last
            val reported = run.round.report {
                crewRepository.quit(run.postId, last?.distance?.meters, last?.elapsed)
            }
            if (reported is Outcome.Failure) _failure.value = reported.error
            finish(run, cancelledReason = null)
        }
    }

    /** 일시정지(F-15c). 폰 버튼·잠금 화면 알림·워치가 모두 RunTracker로 모이고, 서버 보고는 진행값의 정지 전이로 한다. */
    fun pause() {
        if (run?.round?.isMeasuring == true) runTracker.onPauseTapped()
    }

    /** 재개(F-15c). */
    fun resume() {
        if (run?.round?.isMeasuring == true) runTracker.onResumeTapped()
    }

    /**
     * 대기실만 나가기(출발 전, FR-147). 서버에 나가기를 알리고(CrewRepository.leaveLobby) 성공하면 채널을 닫고 이 회를 정리해
     * Idle로 돌린다(종료가 아니므로 Finished를 내지 않는다). 부른 화면의 범위가 취소돼도 알림과 정리는 applicationScope에서 끝낸다.
     * 출발 예약(CrewRunStart)을 받은 뒤에는 ALREADY_STARTED로 거부한다(그만두기는 quit). 정리는 회의 직렬 범위에서 예약 여부를
     * 다시 보고 하므로, 서버가 나가기를 받는 사이 출발 예약이 와도 시작한 회를 닫지 않는다. 실패하면 채널과 대기 상태를 그대로 둔다.
     */
    suspend fun leaveLobby(postId: Long): Outcome<Unit> {
        val current = run?.takeIf { it.postId == postId }
        if (current?.isScheduled == true) return Outcome.Failure(AppError.Rejected(ALREADY_STARTED))
        return applicationScope
            .async {
                when (val left = crewRepository.leaveLobby(postId)) {
                    is Outcome.Success -> if (current == null || closeInRound(current)) {
                        Outcome.Success(Unit)
                    } else {
                        Outcome.Failure(AppError.Rejected(ALREADY_STARTED))
                    }

                    is Outcome.Failure -> left
                }
            }.await()
    }

    /** 회의 직렬 범위에서 조용히 닫는다. 이미 닫힌 회면 참, 그 사이 출발 예약을 받았으면 거짓. */
    private suspend fun closeInRound(run: CrewRun): Boolean {
        val closed = CompletableDeferred<Boolean>()
        val job = run.round.scope.launch { closed.complete(closeQuietly(run)) }
        // 범위가 이미 닫혀 실행되지 못했으면(취소·종료로 정리된 회) 닫을 것이 없다.
        job.invokeOnCompletion { closed.complete(true) }
        return closed.await()
    }

    /** 대기실 응답 하나를 반영한다. 회의 직렬 범위에서만 부른다. */
    private suspend fun apply(run: CrewRun, lobby: Lobby) {
        lobby.me?.memberId?.let { run.myMemberId = it }
        if (run.isScheduled || this.run !== run) return
        val isWaiting = _state.value.let { it is CrewRunState.Countdown || it is CrewRunState.JoinChoice }
        when (lobby.phase) {
            LobbyPhase.COUNTDOWN -> {
                val startAt = lobby.startAt
                if (lobby.isMeCandidate && startAt != null) {
                    _state.value = CrewRunState.Countdown(run.postId, startAt)
                    checkDepartureOnce(run, startAt)
                } else if (isWaiting) {
                    _state.value = CrewRunState.Lobby(run.postId)
                }
            }

            LobbyPhase.RUNNING -> when {
                lobby.isMeRunner -> joinAndStart(run)
                lobby.canJoin -> _state.value = CrewRunState.JoinChoice(run.postId, lobby.countdownParticipantIds.size)
                else -> Unit
            }

            LobbyPhase.LOBBY, null -> if (isWaiting) _state.value = CrewRunState.Lobby(run.postId)

            // 끝·취소는 CrewRunEnded·CrewRunCancelled가 닫는다.
            LobbyPhase.FINISHED, LobbyPhase.CANCELLED -> Unit
        }
    }

    /**
     * 카운트다운이 startAt + [DEPARTURE_GRACE]가 지나도록 출발 사실을 받지 못했으면(신호 유실) 그 회에 한 번만 대기실을 다시 읽어
     * RUNNING·진행 참가자면 시작한다(리뷰 결함 5). 주기 읽기가 아니다. 그 사이 출발 사실로 시작했거나 후보에서 빠졌으면 읽지 않는다.
     */
    private fun checkDepartureOnce(run: CrewRun, startAt: Instant) {
        if (run.isDepartureCheckPlanned) return
        run.isDepartureCheckPlanned = true
        run.round.scope.launch {
            val wait = Duration.between(clock.instant(), startAt).plus(DEPARTURE_GRACE)
            if (!wait.isNegative) delay(wait.toMillis())
            val isHandled = run.isScheduled || run.isDepartureSeen
            if (isHandled || this@CrewRunCoordinator.run !== run || _state.value !is CrewRunState.Countdown) {
                return@launch
            }
            (crewRepository.lobby(run.postId) as? Outcome.Success)?.let { apply(run, it.value) }
        }
    }

    /**
     * 출발 사실(CrewRunDeparture·옛 CrewRunStart): 대기실을 다시 읽어 확인한다(설계 05-32 2.1). 읽기가 끝내 실패하면 출발 사실의 후보에
     * 내가 있을 때만(옛 메시지는 확정 후보에게만 온다) 그 시각부터 잰다. 측정 기록 ID는 앱이 만든다.
     */
    private suspend fun onDeparture(run: CrewRun, postId: Long, startAt: Instant, participantIds: List<Long>?) {
        if (postId != run.postId || run.isScheduled) return
        run.isDepartureSeen = true
        var read = crewRepository.lobby(postId)
        var attempt = 1
        while (read is Outcome.Failure && read.error !is AppError.Rejected && attempt < READ_ATTEMPTS) {
            delay(READ_RETRY_MILLIS * attempt)
            read = crewRepository.lobby(postId)
            attempt++
        }
        when (read) {
            is Outcome.Success -> apply(run, read.value)

            is Outcome.Failure -> {
                val me = run.myMemberId
                val isCandidate = participantIds?.let { me != null && me in it } ?: true
                if (isCandidate) begin(run, startAt, recordId = null)
            }
        }
    }

    /** 내 참여(POST join, 멱등)로 시작 절대시각과 측정 기록 ID를 받아 측정을 시작한다. 이미 끝낸 참여면 끝 상태로 간다. */
    private suspend fun joinAndStart(run: CrewRun) {
        if (run.isScheduled || run.isJoining) return
        run.isJoining = true
        val joined = crewRepository.join(run.postId)
        run.isJoining = false
        when (joined) {
            is Outcome.Success -> if (joined.value.isEnded) {
                finish(run, cancelledReason = null)
            } else {
                begin(run, joined.value.joinedAt, joined.value.measurementRecordId)
            }

            is Outcome.Failure -> _failure.value = joined.error
        }
    }

    /** 결과 화면이 Finished를 보여 준 뒤 부른다. 다음 크루런 화면이 지난 회의 종료 상태를 받지 않게 Idle로 돌린다(S-17). */
    fun acknowledgeFinished() {
        _state.update { if (it is CrewRunState.Finished) CrewRunState.Idle else it }
        _failure.value = null
    }

    /**
     * 내 측정을 [startAt](측정 시작 절대시각)부터 연다. 3초를 더하지 않는다([임시:D-성-881]). 아직 그 시각 전이면 그때까지 Countdown으로
     * 기다린다. 같은 측정 기록 ID는 이 앱 실행에서 한 번만 시작한다.
     */
    private suspend fun begin(run: CrewRun, startAt: Instant, recordId: String?) {
        if (recordId != null && recordId in startedRecordIds) return
        if (!run.schedule()) return
        recordId?.let(startedRecordIds::add)
        val wait = Duration.between(clock.instant(), startAt)
        if (!wait.isNegative && !wait.isZero) {
            _state.value = CrewRunState.Countdown(run.postId, startAt)
            delay(wait.toMillis())
        }
        val base = RunStartSpec(
            mode = RunMode.CREW_RUN,
            cardKind = CardKind.CREW_RUN,
            crewRunPostId = run.postId,
            crewParticipationStartAt = startAt
        )
        val spec = recordId?.let { base.copy(recordId = it) } ?: base
        val started = run.round.begin(spec) { progress(run, it) }
        if (started is Outcome.Failure) {
            fail(run, started.error)
            return
        }
        _state.value = CrewRunState.Running(run.postId, run.lastEntries, run.lastTotal)
    }

    /**
     * 진행값 하나: 화면용 내 측정값을 갱신하고, 정지로 바뀌었으면 서버에 알린다(재개는 다음 진행값이 알린다). 경과가 늘어난
     * 처음 보는 값만 중계 채널로 보낸다. 목표 거리에 닿아도 끝내지 않는다(합산 방식).
     */
    private fun progress(run: CrewRun, p: RunProgress) {
        if (!run.round.isMeasuring) return
        run.last = p
        updateMine(run, p)
        if (p.isPaused != run.isPaused) {
            run.isPaused = p.isPaused
            if (p.isPaused) reportPause(run, p.elapsed)
        }
        if (!run.round.accept(p)) return
        run.channel?.send(
            ProgressReport(
                channel = ProgressReport.Channel.CREW_RUN,
                channelId = run.postId,
                distanceMeters = p.distance.meters,
                elapsedSeconds = p.elapsed.seconds,
                sentAt = clock.instant()
            )
        )
    }

    /** 정지 보고. 실패해도 측정은 그대로 두고(다른 사람에게 멈춤이 늦게 보일 뿐) 다시 보내기는 CoordinatedRound에 맡긴다. */
    private fun reportPause(run: CrewRun, elapsed: Duration) {
        run.round.scope.launch { run.round.report { crewRepository.pause(run.postId, elapsed) } }
    }

    private fun updateMine(run: CrewRun, p: RunProgress) {
        val mine = CrewRunProgress(
            p.distance,
            p.elapsed,
            p.currentPace,
            p.averagePace,
            isPaused = p.isPaused,
            route = runTracker.routeLine.value
        )
        _state.update { current ->
            if (current is CrewRunState.Running && current.postId == run.postId) current.copy(mine = mine) else current
        }
    }

    private fun handle(run: CrewRun, m: ServerMessage) {
        when (m) {
            is CrewRunStart -> run.round.scope.launch { onDeparture(run, m.postId, m.startAt, participantIds = null) }

            is CrewRunDeparture -> run.round.scope.launch { onDeparture(run, m.postId, m.startAt, m.participantIds) }

            // 변경 신호는 보이는 화면(CrewReadSession)이 읽는다. 조정자는 대기실 응답(onLobby)으로만 바뀐다.
            is CrewRunChanged -> Unit

            is CrewRunRelay -> onRelay(run, m)

            is CrewRunCancelled -> if (m.postId == run.postId) finish(run, m.reason)

            is CrewRunEnded -> if (m.postId == run.postId) finish(run, cancelledReason = null)

            // 대결 메시지는 이 조정자와 무관하다. 메시지 종류가 늘면 컴파일러가 여기를 알린다(S-10).
            is MatchFound, is MatchFailed, is BattleStart, is OpponentProgress -> Unit

            is FriendInviteDeclined, is FriendInviteExpired -> Unit

            // 채팅은 같은 연결을 쓰는 ChatRoom이 받는다(D-103).
            is CrewChatPosted, is CrewChatRemoved, is CrewChatReactionChanged -> Unit

            is BattleGuidance, is CountdownStarted, is BattleEnded -> Unit
        }
    }

    /** [postId] 회의 측정 전 상태(대기·카운트다운·참여 선택)인가. */
    private fun CrewRunState.isBeforeStart(postId: Long): Boolean = when (this) {
        is CrewRunState.Lobby -> this.postId == postId
        is CrewRunState.Countdown -> this.postId == postId
        is CrewRunState.JoinChoice -> this.postId == postId
        else -> false
    }

    /** 중계(참가자 표·합산 거리)를 상태로 내보낸다. 출발 카운트다운·종료 상태는 덮지 않는다(F-1). */
    private fun onRelay(run: CrewRun, m: CrewRunRelay) {
        if (m.postId != run.postId) return
        run.remember(m.entries, m.totalDistance)
        _state.update { current ->
            when (current) {
                is CrewRunState.Countdown, is CrewRunState.Finished, is CrewRunState.JoinChoice -> current

                is CrewRunState.Running -> current.copy(entries = m.entries, totalDistance = m.totalDistance)

                // 내 측정을 시작하지 않은 대기실(출발 뒤 아직 참여하지 않은 입장자)은 중계로 진행 화면이 되지 않는다.
                is CrewRunState.Lobby -> if (run.isScheduled) {
                    CrewRunState.Running(
                        run.postId,
                        m.entries,
                        m.totalDistance
                    )
                } else {
                    current
                }

                else -> CrewRunState.Running(run.postId, m.entries, m.totalDistance)
            }
        }
    }

    private fun finish(run: CrewRun, cancelledReason: String?) {
        val ended = run.round.endMeasuring()
        close(run, cancelledReason, (ended as? FinishOutcome.Saved)?.run?.recordId)
    }

    private fun fail(run: CrewRun, error: AppError) {
        _failure.value = error
        finish(run, cancelledReason = null)
    }

    private fun close(run: CrewRun, cancelledReason: String?, recordId: String?) {
        if (this.run !== run) return
        this.run = null
        run.channel?.close()
        _state.value = CrewRunState.Finished(run.postId, cancelledReason, recordId)
        run.round.cancel()
    }

    /**
     * 측정 전 회를 결과 없이 닫는다(대기실 나가기). 채널을 닫고 범위를 취소한 뒤 Idle로 돌린다. 회의 직렬 범위에서만 부른다.
     * 출발 예약을 받았으면 닫지 않고 거짓, 이미 다른 회로 바뀌었으면 닫을 것이 없어 참.
     */
    private fun closeQuietly(run: CrewRun): Boolean {
        if (run.isScheduled) return false
        if (this.run !== run) return true
        this.run = null
        run.channel?.close()
        _state.update { if (it.isBeforeStart(run.postId)) CrewRunState.Idle else it }
        run.round.cancel()
        return true
    }

    /** 이전 회가 남아 있으면 정리(측정 중이면 endRun, 채널 닫기, 범위 취소)하고 새 회를 연다. */
    private fun openRun(postId: Long, distance: Distance?): CrewRun {
        run?.let { previous ->
            run = null
            previous.round.endMeasuring()
            previous.channel?.close()
            previous.round.cancel()
        }
        _failure.value = null
        return CrewRun(postId, distance).also { run = it }
    }

    /** 크루런 한 회. 범위·측정 여부는 CoordinatedRound가 맡는다(S-2, O-3). 범위 안 예외는 연결 실패로 닫는다. */
    private inner class CrewRun(val postId: Long, val distance: Distance?) {
        val round = CoordinatedRound(applicationScope, runTracker) { fail(this, AppError.Network) }

        @Volatile
        var channel: LiveChannel? = null
            private set

        /** 마지막 중계 표와 합산 거리. 측정이 시작되면 Running 상태의 첫 표로 쓴다. */
        var lastEntries: List<CrewRunRelay.Entry> = emptyList()
            private set

        var lastTotal: Distance = Distance.ZERO
            private set

        /** 마지막으로 받은 내 진행값(끝내기 보고에 싣는다). 직렬 디스패처에서만 쓴다. */
        var last: RunProgress? = null

        /** 마지막으로 본 정지 여부. 직렬 디스패처에서만 쓴다. */
        var isPaused = false

        /** 대기실 응답의 내 회원 ID(출발 사실의 후보 대조에 쓴다). */
        @Volatile
        var myMemberId: Long? = null

        /** 카운트다운 끝 확인 읽기를 이미 잡았다(회마다 한 번). 직렬 디스패처에서만 쓴다. */
        var isDepartureCheckPlanned = false

        /** 출발 사실을 받아 확인 중이다(카운트다운 끝 확인 읽기를 하지 않는다). 직렬 디스패처에서만 쓴다. */
        var isDepartureSeen = false

        /** 참여 요청 중(직렬 디스패처에서만 쓴다). 같은 참여를 겹쳐 보내지 않는다. */
        var isJoining = false

        /** 측정 시작을 예약했는가. 출발 사실이 두 번 와도 한 번만 시작한다. */
        @Volatile
        var isScheduled = false
            private set

        fun attach(channel: LiveChannel) {
            this.channel = channel
        }

        fun remember(entries: List<CrewRunRelay.Entry>, total: Distance) {
            lastEntries = entries
            lastTotal = total
        }

        /** 시작 예약. 이미 예약됐으면 거짓. */
        fun schedule(): Boolean {
            if (isScheduled) return false
            isScheduled = true
            return true
        }
    }

    private companion object {
        /** 출발 확인 읽기(GET 대기실) 횟수와 간격(1·2초 뒤 다시). */
        const val READ_ATTEMPTS = 3
        const val READ_RETRY_MILLIS = 1_000L

        /** 출발 예약 시각 뒤 출발 사실을 기다리는 여유(서버 1초 스케줄 지연). 지나면 한 번 대기실을 읽는다. */
        val DEPARTURE_GRACE: Duration = Duration.ofSeconds(1)

        /** 서버 common.ReasonCode.ALREADY_STARTED와 같은 이름. */
        const val ALREADY_STARTED = "ALREADY_STARTED"
    }
}
