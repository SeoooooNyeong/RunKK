package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.MyAvatar
import com.a207.runkk.app.domain.account.myAvatar
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunCoordinator
import com.a207.runkk.app.domain.crew.CrewRunResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * 다시 읽기는 화면 수명을 따른다(확인 리뷰 CR2-A1): 화면이 가려지거나 앱이 백그라운드면([onStop]) 멈추고, 돌아오면([onStart]) 바로 한 번 읽은
 * 뒤 같은 간격 규칙을 잇는다. 상한 [RUNNING_WATCH_MILLIS]는 처음 읽은 때부터의 실제 경과([timeSource])로 센다.
 *
 * 가운데 내 아바타는 홈과 같은 출처(본인 요약의 얼굴·배경, AccountRepository.myAvatar)로 한 번 읽는다([myAvatar]).
 */
@HiltViewModel
class CrewRunResultViewModel internal constructor(
    private val crews: CrewRepository,
    private val coordinator: CrewRunCoordinator,
    private val timeSource: TimeSource,
    private val myAvatar: suspend () -> MyAvatar? = { null },
    private val details: CrewRunDetailSource
) : ViewModel() {
    @Inject
    constructor(
        crews: CrewRepository,
        coordinator: CrewRunCoordinator,
        details: CrewRunDetailSource,
        account: AccountRepository
    ) : this(crews, coordinator, TimeSource.Monotonic, { account.myAvatar() }, details)

    private val _state = MutableStateFlow(CrewRunResultUiState())
    val state: StateFlow<CrewRunResultUiState> = _state.asStateFlow()
    private var loaded: Long? = null
    private var job: Job? = null
    private var detailJob: Job? = null
    private var avatarJob: Job? = null

    /** 지금까지 다시 읽은 횟수(간격 규칙의 위치). */
    private var rereads = 0

    /** 처음 읽은 때. 상한을 실제 경과로 센다. */
    private var watchStart: TimeMark? = null

    /** 화면이 가려져 다시 읽기를 멈췄고 아직 더 읽어야 한다. */
    private var isPaused = false

    fun load(postId: Long, isForced: Boolean = false) {
        if (!isForced && loaded == postId) return
        loaded = postId
        rereads = 0
        watchStart = null
        isPaused = false
        _state.update { it.copy(isLoading = true, error = null) }
        watch(postId)
        readMyAvatar()
    }

    /** 내 아바타(홈과 같은 얼굴·배경)를 한 번 읽는다. 못 읽으면 기본 아바타 그대로다. */
    private fun readMyAvatar() {
        if (_state.value.myAvatar != null || avatarJob?.isActive == true) return
        avatarJob = viewModelScope.launch {
            myAvatar()?.let { look -> _state.update { it.copy(myAvatar = look) } }
        }
    }

    /** 화면이 다시 보인다. 가려져 멈췄던 다시 읽기를 바로 한 번 읽고 잇는다. */
    fun onStart() {
        val postId = loaded ?: return
        if (!isPaused || job?.isActive == true) return
        isPaused = false
        watch(postId)
    }

    /** 화면이 가려졌다(다른 화면을 위에 쌓음·백그라운드). 다시 읽기를 멈춘다. */
    fun onStop() {
        if (job?.isActive != true) return
        job?.cancel()
        isPaused = true
    }

    private fun watch(postId: Long) {
        job?.cancel()
        job = viewModelScope.launch {
            while (true) {
                val last = read(postId) ?: return@launch
                val start = watchStart ?: timeSource.markNow().also { watchStart = it }
                if (!last.needsReread(rereads, start.elapsedNow().inWholeMilliseconds)) {
                    isPaused = false
                    return@launch
                }
                val pause = rereadDelay(rereads)
                rereads++
                delay(pause)
            }
        }
    }

    /** 결과를 한 번 읽어 화면에 반영하고, 지금 보이는 결과를 돌려준다. 처음 읽기부터 실패하면 오류를 보이고 null. */
    private suspend fun read(postId: Long): CrewRunResult? {
        when (val r = crews.crewRunResult(postId)) {
            is Outcome.Success -> {
                _state.update { it.copy(isLoading = false, result = r.value, error = null) }
                coordinator.acknowledgeFinished()
                readDetail(r.value.myRecordId)
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
