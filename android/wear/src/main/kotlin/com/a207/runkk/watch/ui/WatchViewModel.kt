package com.a207.runkk.watch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.watch.link.PhoneLink
import com.a207.runkk.wear.contract.HeartRateBatchMessage
import com.a207.runkk.wear.contract.WatchDisplayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 워치 화면 상태를 만든다. 화면 규칙(심박 빈칸 30초, 연결 끊김, 시작 요청 기다림, 안내 표시 시간)은 여기에만 둔다.
 * 폰과의 통신·수집 제어는 [PhoneLink]가 맡는다(5.1.2 watch.ui → watch.link).
 */
@HiltViewModel
class WatchViewModel @Inject constructor(private val phoneLink: PhoneLink, private val clock: Clock) : ViewModel() {
    private data class Received(val display: WatchDisplayMessage, val atMillis: Long)

    private data class LocalEvents(
        val notice: String? = null,
        val noticeAtMillis: Long = 0,
        val startRequestedAtMillis: Long? = null
    )

    private data class Inputs(
        val received: Received?,
        val connected: Boolean,
        val permissionNeeded: Boolean,
        val latestHeartRate: HeartRateBatchMessage.Sample?,
        val events: LocalEvents
    )

    private val received = MutableStateFlow<Received?>(null)
    private val events = MutableStateFlow(LocalEvents())

    /** 시간이 지나며 바뀌는 규칙(빈칸·끊김·안내 만료)을 다시 계산하는 1초 박자. */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    val uiState: StateFlow<WatchUiState> = combine(
        received,
        phoneLink.phoneConnected,
        phoneLink.heartRatePermissionNeeded,
        phoneLink.latestHeartRate,
        events,
        ::Inputs
    )
        .combine(ticker) { inputs, _ -> reduce(inputs, clock.millis()) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WatchUiState())

    init {
        viewModelScope.launch { phoneLink.displays.collect { onDisplay(it) } }
    }

    /** 시작 버튼(FR-183). 폰과 연결되지 않았으면 "폰과 연결 필요"를 보인다. 측정은 폰이 시작한다(C-002). */
    fun onStartTapped() {
        events.update { it.copy(startRequestedAtMillis = clock.millis(), notice = null) }
        viewModelScope.launch {
            if (!phoneLink.sendStartRequest()) {
                events.update {
                    it.copy(
                        startRequestedAtMillis = null,
                        notice = NOT_CONNECTED_NOTICE,
                        noticeAtMillis = clock.millis()
                    )
                }
            }
        }
    }

    /** OS 심박 권한 창이 닫혔거나 화면으로 돌아왔다. */
    fun onPermissionResult() = phoneLink.onHeartRatePermissionChanged()

    private fun onDisplay(display: WatchDisplayMessage) {
        val now = clock.millis()
        received.value = Received(display, now)
        events.update { current ->
            when {
                display.notice != null ->
                    current.copy(notice = display.notice, noticeAtMillis = now, startRequestedAtMillis = null)

                display.running -> current.copy(startRequestedAtMillis = null)

                else -> current
            }
        }
    }

    private fun reduce(inputs: Inputs, now: Long): WatchUiState {
        val received = inputs.received
        val display = received?.display
        val running = display?.running == true
        val allowed = display?.heartRateCollectionAllowed == true
        val freshLocal = inputs.latestHeartRate
            ?.takeIf { now - it.measuredAtEpochMillis <= HEART_RATE_STALE_MILLIS }
            ?.bpm
        val displayStale = received == null || now - received.atMillis > LINK_LOST_MILLIS
        val requestedAt = inputs.events.startRequestedAtMillis
        return WatchUiState(
            phoneConnected = inputs.connected,
            running = running,
            elapsedSeconds = display?.elapsedSeconds ?: 0,
            distanceMeters = display?.distanceMeters ?: 0,
            paceSecondsPerKm = display?.currentPaceSecondsPerKm,
            heartRateBpm = if (allowed) freshLocal ?: display?.heartRateBpm else null,
            planDistanceMeters = display?.planDistanceMeters,
            planPaceSecondsPerKm = display?.planPaceSecondsPerKm,
            linkLost = running && (!inputs.connected || displayStale),
            startPending = !running && requestedAt != null && now - requestedAt < START_PENDING_MILLIS,
            heartRatePermissionNeeded = inputs.permissionNeeded,
            notice = inputs.events.notice?.takeIf { now - inputs.events.noticeAtMillis < NOTICE_MILLIS }
        )
    }

    internal companion object {
        /** 이보다 오래된 심박은 빈칸(6.1.15 WatchRunDisplay). */
        const val HEART_RATE_STALE_MILLIS = 30_000L

        /** 러닝 중 폰 표시가 이만큼 없으면 연결 끊김으로 보인다(폰은 1초마다 보낸다). */
        const val LINK_LOST_MILLIS = 5_000L

        /** 안내 문구를 보이는 시간. */
        const val NOTICE_MILLIS = 5_000L

        /** 시작 요청 뒤 폰의 응답을 기다리는 시간. */
        const val START_PENDING_MILLIS = 10_000L

        /** FR-183 "연결되어 있지 않으면 거부하고 '폰과 연결 필요'를 보인다". */
        const val NOT_CONNECTED_NOTICE = "폰과 연결 필요"

        private const val TICK_MILLIS = 1_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
