package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.CrewRunReturnReceipt
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.MyAvatar
import com.a207.runkk.app.domain.account.myAvatar
import com.a207.runkk.app.domain.crew.CrewChange
import com.a207.runkk.app.domain.crew.CrewReadSession
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunCompletion
import com.a207.runkk.app.domain.crew.CrewRunCoordinator
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.CrewRunSignal
import com.a207.runkk.app.domain.crew.CrewRunSignals
import com.a207.runkk.app.domain.crew.CrewRunState
import com.a207.runkk.app.domain.crew.MyRecordUpload
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 크루런 결과(합산 방식, F-16·F-16b): 크루 합산 거리와 참가자별 거리, 내 기록 상세. 내 기록 ID가 생기면 상세(GET /runs/{id})를 한 번 읽는다.
 *
 * 다시 읽기(MR !91 AN-3): 아직 달리는 크루원이 있으면(RUNNING) 크루런이 닫힐 때까지 읽되 처음 [REREADS]번은 [REREAD_MILLIS] 간격, 그
 * 뒤로는 간격을 두 배씩 늘려 [MAX_REREAD_MILLIS]까지 둔다(배터리). 크루런은 거리 × 15분 뒤 서버가 닫지만 그래도 [RUNNING_WATCH_MILLIS]를
 * 넘기면 멈춘다. 크루런은 닫혔고 내 기록만 아직이면(myRecordId 없음) [REREAD_MILLIS] 간격으로 [REREADS]번까지만 읽는다. 읽기가 실패해도
 * 이미 받은 결과가 있으면 같은 규칙으로 이어 간다.
 *
 * 다시 읽기는 화면 수명을 따른다(확인 리뷰 CR2-A1): 처음 읽기 뒤 주기 다시 읽기는 화면이 STARTED일 때만 돈다. 가려지거나 앱이
 * 백그라운드면([onStop]) 멈추고, 돌아오면([onStart]) 바로 한 번 읽은 뒤 같은 간격 규칙을 잇는다. 상한 [RUNNING_WATCH_MILLIS]는 처음 읽은
 * 때부터의 실제 경과([timeSource])로 센다.
 *
 * 실시간 연결(CrewRunSignals.watch)이 CONNECTED인 동안은 주기 다시 읽기를 쉬고 결과 변경 신호(RESULT)만 따른다. 연결이 끊기면 간격 규칙을
 * 처음(3초)부터 다시 시작한다.
 *
 * 가운데 내 아바타는 홈과 같은 출처(본인 요약의 얼굴·배경, AccountRepository.myAvatar)로 한 번 읽는다([myAvatar]).
 *
 * 「확인」(설계 05-32 [임시:D-성-888]·[임시:D-성-890], TC-11016·11044): 이 화면이 방금 끝낸 내 측정의 기록(조정자 Finished.recordId)만
 * 기다린다. 서버가 받았다(STORED·ALREADY_STORED, 또는 결과 응답이 그 기록을 이었다)고 확인되면 바로 복귀 값(내 유효 거리)과 함께 나간다.
 * 다른 크루원의 종료·업로드나 크루 집계는 기다리지 않는다. 내 전송 실패는 다시 보내기, 서버 거부·기기 저장 실패는 안내 뒤 복귀 값
 * 없이 나간다. 기록이 없으면(측정 전 끝·기록 미생성, 지난 결과 열람) 기다리지 않고 나간다. 기존 결과 다시 읽기는 그대로다.
 * 크루런 결과 변경 신호(RESULT)를 받으면 결과를 한 번 더 읽는다(화면이 보일 때만).
 */
@HiltViewModel
class CrewRunResultViewModel internal constructor(
    private val crews: CrewRepository,
    private val coordinator: CrewRunCoordinator,
    private val timeSource: TimeSource,
    private val myAvatar: suspend () -> MyAvatar? = { null },
    /** 첫 그림의 내 아바타(기기에 남긴 마지막 모습, 없으면 null). */
    lastAvatar: () -> MyAvatar? = { null },
    /** 내 기록 저장·업로드 단계(CrewRunCompletion.observe: 내 기록 ID, 결과 응답이 이은 내 기록 ID). */
    private val observeUpload: (
        String,
        Flow<String?>
    ) -> Flow<MyRecordUpload> = { _, _ -> flowOf(MyRecordUpload.Waiting) },
    /** 내 기록 다시 보내기(CrewRunCompletion.retry). */
    private val retryUpload: (String) -> Unit = {},
    /** 크루런 변경 신호와 실시간 연결 여부(CrewRunSignals.watch). */
    private val signals: () -> Flow<CrewRunSignal> = { emptyFlow() },
    private val now: () -> Instant = Instant::now,
    private val details: CrewRunDetailSource
) : ViewModel() {
    @Inject
    constructor(
        crews: CrewRepository,
        coordinator: CrewRunCoordinator,
        details: CrewRunDetailSource,
        account: AccountRepository,
        completion: CrewRunCompletion,
        signals: CrewRunSignals
    ) : this(
        crews,
        coordinator,
        TimeSource.Monotonic,
        { account.myAvatar() },
        account::lastMyAvatar,
        completion::observe,
        completion::retry,
        signals::watch,
        Instant::now,
        details
    )

    private val _events = Channel<CrewRunResultEvent>(Channel.BUFFERED)

    /** 화면 이동(「확인」 뒤 복귀). */
    val events: Flow<CrewRunResultEvent> = _events.receiveAsFlow()

    /** 이 화면이 방금 끝낸 내 측정의 기록 ID. 이 결과로 들어오며 조정자 Finished에서 읽는다. 없으면 기다릴 기록이 없다. */
    private var ownRecordId: String? = null
    private var confirmJob: Job? = null
    private var signalJob: Job? = null

    // 기기에 남긴 마지막 내 아바타(AccountRepository.lastMyAvatar)를 첫 값으로 그려 기본 아바타가 보였다가 바뀌지 않게 한다.
    private val _state = MutableStateFlow(CrewRunResultUiState(myAvatar = lastAvatar()))
    private var isAvatarRead = false
    val state: StateFlow<CrewRunResultUiState> = _state.asStateFlow()
    private var loaded: Long? = null
    private var job: Job? = null
    private var detailJob: Job? = null
    private var avatarJob: Job? = null

    /** 얼굴 그림을 받으러 간 크루원 얼굴 키(다시 읽을 때 또 받지 않는다). */
    private val requestedFaces = mutableSetOf<String>()

    /** 지금까지 다시 읽은 횟수(간격 규칙의 위치). */
    private var rereads = 0

    /** 처음 읽은 때. 상한을 실제 경과로 센다. */
    private var watchStart: TimeMark? = null

    /** 화면이 STARTED가 아니거나 가려져 다시 읽기를 멈췄고 아직 더 읽어야 한다. */
    private var isPaused = false

    /** 화면이 STARTED다(onStart ~ onStop). */
    private var isStarted = false

    /** 한 번 가려졌다가 돌아온다(돌아오면 바로 한 번 읽는다). */
    private var wasHidden = false

    /** 실시간 연결 중이다(주기 다시 읽기를 쉰다). */
    private var isLive = false

    /** 마지막 결과로 보아 아직 더 읽어야 한다. */
    private var needsMore = false

    /**
     * 결과 읽기 하나로 합친다(리뷰 결함 3): 간격 다시 읽기와 결과 변경 신호가 같은 읽기를 부르고, 읽는 중에 온 요청은 끝난 뒤 한 번만
     * 더 읽는다. [job]은 다음 간격 읽기 예약이다.
     */
    private val resultReads = CrewReadSession(viewModelScope) { readAndPlan() }

    /** 이번 읽기가 간격 예약으로 왔다(간격 규칙의 위치를 한 칸 옮긴다). */
    private var isTimerRead = false

    fun load(postId: Long, isForced: Boolean = false) {
        if (!isForced && loaded == postId) return
        // 결과를 읽으면 조정자가 Idle로 돌아가므로 먼저 내 기록 ID를 잡아 둔다.
        (coordinator.state.value as? CrewRunState.Finished)?.takeIf { it.postId == postId }?.recordId?.let {
            ownRecordId = it
        }
        loaded = postId
        rereads = 0
        watchStart = null
        isPaused = false
        wasHidden = false
        _state.update { it.copy(isLoading = true, error = null) }
        job?.cancel()
        resultReads.cancel()
        resultReads.refresh()
        readMyAvatar()
    }

    /** 내 아바타(홈과 같은 얼굴·배경)를 한 번 읽는다. 못 읽으면 기본 아바타 그대로다. */
    private fun readMyAvatar() {
        if (isAvatarRead || avatarJob?.isActive == true) return
        avatarJob = viewModelScope.launch {
            myAvatar()?.let { look ->
                isAvatarRead = true
                _state.update { it.copy(myAvatar = look) }
            }
        }
    }

    /**
     * 「확인」: 내 기록이 서버에 저장될 때까지 기다린 뒤 복귀 값과 함께 나간다(설계 05-32 2.4). 다시 보낼 수 있는 실패면 다시
     * 보내고 기다린다. 다시 보내도 안 되는 실패를 안내한 뒤 또 누르면 복귀 값 없이 나간다.
     */
    fun onConfirm() {
        val recordId = ownRecordId
        when (_state.value.confirm) {
            ConfirmState.WAITING -> return

            ConfirmState.BLOCKED -> {
                leave(receipt = null)
                return
            }

            ConfirmState.RETRYABLE -> recordId?.let(retryUpload)

            ConfirmState.IDLE -> Unit
        }
        if (recordId == null) {
            leave(receipt = null)
            return
        }
        _state.update { it.copy(confirm = ConfirmState.WAITING) }
        confirmJob?.cancel()
        confirmJob = viewModelScope.launch {
            var isDone = false
            observeUpload(recordId, _state.map { it.result?.myRecordId }).collect { upload ->
                // 나간 뒤 상태 변화로 다시 오는 값은 버린다(복귀 값은 한 번만).
                if (isDone) return@collect
                when (upload) {
                    is MyRecordUpload.Stored -> {
                        isDone = true
                        leave(receiptOf(recordId, upload))
                        confirmJob?.cancel()
                    }

                    is MyRecordUpload.Retryable -> _state.update {
                        it.copy(confirm = ConfirmState.RETRYABLE, error = CrewErrorText.of(upload.error))
                    }

                    MyRecordUpload.Blocked -> _state.update { it.copy(confirm = ConfirmState.BLOCKED) }

                    MyRecordUpload.Waiting -> _state.update { it.copy(confirm = ConfirmState.WAITING, error = null) }
                }
            }
        }
    }

    /** 내 기록이 저장됐다: 내 유효 거리(유효하지 않으면 0)를 담은 복귀 값. receiptId는 기록 ID라 같은 기록을 두 번 강조하지 않는다. */
    private fun receiptOf(recordId: String, stored: MyRecordUpload.Stored): CrewRunReturnReceipt {
        val mine = _state.value.result?.participants?.firstOrNull { it.isMe }?.distance
        val meters = if (stored.isValid) (stored.distance ?: mine)?.meters ?: 0 else 0
        return CrewRunReturnReceipt(
            receiptId = recordId,
            postId = loaded ?: 0,
            myRecordId = recordId,
            ownDistanceMeters = meters,
            uploadedAt = now()
        )
    }

    private fun leave(receipt: CrewRunReturnReceipt?) {
        _state.update { it.copy(confirm = ConfirmState.IDLE) }
        _events.trySend(CrewRunResultEvent.Leave(receipt))
    }

    /** 화면이 보인다(STARTED). 가려졌다 돌아왔으면 바로 한 번 읽고, 처음 보이면 간격 규칙대로 다음 읽기를 잡는다. */
    fun onStart() {
        isStarted = true
        listenSignals()
        if (loaded == null || !isPaused || job?.isActive == true) return
        isPaused = false
        if (wasHidden) {
            wasHidden = false
            resultReads.refresh()
        } else if (!isLive) {
            scheduleNext()
        }
    }

    /**
     * 이 크루런의 결과 변경 신호(RESULT)를 받으면 결과를 한 번 더 읽고, 실시간 연결이 CONNECTED면 주기 다시 읽기를 쉬며 끊기면 3초부터
     * 다시 시작한다(보이는 동안만, 설계 05-32 5.3·05-33 2.4).
     */
    private fun listenSignals() {
        if (signalJob?.isActive == true) return
        signalJob = viewModelScope.launch {
            signals().collect { signal ->
                when (signal) {
                    is CrewRunSignal.Changed -> onChange(signal.change)
                    is CrewRunSignal.Link -> onLink(signal.isConnected)
                }
            }
        }
    }

    private fun onChange(change: CrewChange) {
        val postId = loaded
        if (postId != null && change.postId == postId && change.resource == CrewChange.Resource.RESULT) {
            resultReads.refresh()
        }
    }

    private fun onLink(isConnected: Boolean) {
        val wasLive = isLive
        isLive = isConnected
        if (isConnected) {
            job?.cancel()
            return
        }
        if (!wasLive || !isStarted || loaded == null || !needsMore || resultReads.isReading) return
        rereads = 0
        scheduleNext()
    }

    /** 화면이 가려졌다(다른 화면을 위에 쌓음·백그라운드). 다시 읽기와 신호 받기를 멈춘다. */
    fun onStop() {
        isStarted = false
        isLive = false
        signalJob?.cancel()
        signalJob = null
        if (!needsMore && job?.isActive != true && !resultReads.isReading) return
        job?.cancel()
        resultReads.cancel()
        isPaused = true
        wasHidden = true
    }

    /**
     * 결과를 한 번 읽고 다음 간격 읽기를 정한다(기존 규칙 그대로: 처음 [REREADS]번 3초, 뒤로 두 배씩 최대 60초, 상한 [RUNNING_WATCH_MILLIS]).
     * 신호로 읽었을 때 간격 예약이 남아 있으면 그 예약을 그대로 둔다(간격 규칙을 앞당기지 않는다).
     */
    private suspend fun readAndPlan() {
        val postId = loaded ?: return
        val wasTimer = isTimerRead
        isTimerRead = false
        val last = read(postId)
        if (last == null) {
            job?.cancel()
            return
        }
        val start = watchStart ?: timeSource.markNow().also { watchStart = it }
        needsMore = last.needsReread(rereads, start.elapsedNow().inWholeMilliseconds)
        if (!needsMore) {
            job?.cancel()
            isPaused = false
            return
        }
        if (!isStarted) {
            isPaused = true
            return
        }
        if (isLive) return
        if (job?.isActive == true && !wasTimer) return
        scheduleNext()
    }

    /** 간격 규칙의 다음 다시 읽기를 잡는다. */
    private fun scheduleNext() {
        val pause = rereadDelay(rereads)
        rereads++
        job = viewModelScope.launch {
            delay(pause)
            isTimerRead = true
            resultReads.refresh()
        }
    }

    /** 결과를 한 번 읽어 화면에 반영하고, 지금 보이는 결과를 돌려준다. 처음 읽기부터 실패하면 오류를 보이고 null. */
    private suspend fun read(postId: Long): CrewRunResult? {
        when (val r = crews.crewRunResult(postId)) {
            is Outcome.Success -> {
                _state.update { it.copy(isLoading = false, result = r.value, error = null) }
                coordinator.acknowledgeFinished()
                readDetail(r.value.myRecordId)
                readMateFaces(r.value)
            }

            is Outcome.Failure -> {
                if (_state.value.result == null) {
                    loaded = null
                    _state.update { it.copy(isLoading = false, error = CrewErrorText.of(r.error)) }
                }
            }
        }
        return _state.value.result
    }

    private fun readDetail(recordId: String?) {
        if (recordId == null || _state.value.detail != null || detailJob?.isActive == true) return
        detailJob = viewModelScope.launch {
            (details.detail(recordId) as? Outcome.Success)?.let { read ->
                _state.update { it.copy(detail = read.value) }
            }
        }
    }

    /** 아바타 줄 크루원의 셀카 얼굴 부위 그림을 얼굴 키마다 한 번 받는다. 받지 못하면 얼굴 키만으로 그린다. */
    private fun readMateFaces(result: CrewRunResult) {
        val keys = result.participants.filter { !it.isMe }.mapNotNull { it.faceKey }.filter { requestedFaces.add(it) }
        // 받아 둔 그림(대기실에서 본 크루원)은 결과와 함께 바로 그린다(기본 머리를 거치지 않게).
        val cached = keys.mapNotNull { key ->
            crews.cachedFaceLayers(key)?.takeIf { it.isNotEmpty() }?.let { key to it }
        }
        if (cached.isNotEmpty()) _state.update { it.copy(mateFaces = it.mateFaces + cached) }
        keys.filter { key -> cached.none { it.first == key } }.forEach { key ->
            viewModelScope.launch {
                (crews.faceLayers(key) as? Outcome.Success)?.value?.takeIf { it.isNotEmpty() }?.let { layers ->
                    _state.update { it.copy(mateFaces = it.mateFaces + (key to layers)) }
                }
            }
        }
    }

    /** 아직 달리는 크루원이 있으면 상한 시간까지, 내가 참가자인데 내 기록이 아직이면 [REREADS]번까지. */
    private fun CrewRunResult.needsReread(rereads: Int, waited: Long): Boolean = when {
        isRunning -> waited < RUNNING_WATCH_MILLIS
        myRecordId == null && participants.any { it.isMe } -> rereads < REREADS
        else -> false
    }

    internal companion object {
        const val REREAD_MILLIS = 3_000L
        const val REREADS = 10

        /** 늘린 간격의 상한. */
        const val MAX_REREAD_MILLIS = 60_000L

        /** 진행 중 결과를 지켜보는 상한(가장 긴 10km 크루런 150분 + 여유). */
        const val RUNNING_WATCH_MILLIS = 3 * 60 * 60_000L

        private const val MAX_DOUBLINGS = 5

        /** [reread]번째 다시 읽기 전 기다림: 처음 [REREADS]번은 [REREAD_MILLIS], 그 뒤 6·12·24·48초, 이후 [MAX_REREAD_MILLIS]. */
        fun rereadDelay(reread: Int): Long {
            if (reread < REREADS) return REREAD_MILLIS
            val doublings = (reread - REREADS + 1).coerceAtMost(MAX_DOUBLINGS)
            return (REREAD_MILLIS shl doublings).coerceAtMost(MAX_REREAD_MILLIS)
        }
    }
}
