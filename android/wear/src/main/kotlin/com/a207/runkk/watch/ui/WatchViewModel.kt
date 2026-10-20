package com.a207.runkk.watch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.watch.link.PhoneLink
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 워치 화면 상태를 만든다. 화면 규칙(심박 빈칸 30초, 연결 끊김, 시작 요청 기다림, 안내 표시 시간)은 여기에만 둔다.
 * 폰과의 통신·수집 제어는 [PhoneLink]가 맡는다(5.1.2 watch.ui → watch.link).
 * wear.contract 형식은 import하지 않고 PhoneLink가 내준 값을 받자마자 이 클래스의 비공개 값으로 옮긴다.
 */
@HiltViewModel
class WatchViewModel @Inject constructor(private val phoneLink: PhoneLink, private val clock: Clock) : ViewModel() {
    /** 폰 표시 정보 중 화면이 쓰는 값(WatchDisplayMessage에서 옮김)과 받은 시각. */
    private data class Received(
        val running: Boolean,
        val elapsedSeconds: Long,
        val distanceMeters: Long,
        val paceSecondsPerKm: Int?,
        val heartRateBpm: Int?,
        val planDistanceMeters: Long?,
        val planPaceSecondsPerKm: Int?,
        val heartRateAllowed: Boolean,
        val notice: String?,
        val atMillis: Long
    )

    /** 워치가 마지막으로 잰 정상 심박. */
    private data class LocalHeartRate(val measuredAtEpochMillis: Long, val bpm: Int)

    private data class LocalEvents(
        val notice: WatchNotice? = null,
        val noticeAtMillis: Long = 0,
        val startRequestedAtMillis: Long? = null
    )

    private data class Inputs(
        val received: Received?,
        val connected: Boolean,
        val permissionNeeded: Boolean,
        val latestHeartRate: LocalHeartRate?,
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
        phoneLink.latestHeartRate.map { sample -> sample?.let { LocalHeartRate(it.measuredAtEpochMillis, it.bpm) } },
        events,
        ::Inputs
    )
        .combine(ticker) { inputs, _ -> reduce(inputs, clock.millis()) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WatchUiState())

    init {
        viewModelScope.launch {
            phoneLink.displays.collect { display ->
                onDisplay(
                    Received(
                        running = display.running,
                        elapsedSeconds = display.elapsedSeconds,
                        distanceMeters = display.distanceMeters,
                        paceSecondsPerKm = display.currentPaceSecondsPerKm,
                        heartRateBpm = display.heartRateBpm,
                        planDistanceMeters = display.planDistanceMeters,
                        planPaceSecondsPerKm = display.planPaceSecondsPerKm,
                        heartRateAllowed = display.heartRateCollectionAllowed,
                        notice = display.notice,
                        atMillis = phoneLink.lastDisplayAtMillis.value ?: clock.millis()
                    )
                )
            }
        }
    }

    /**
     * 시작 버튼(FR-183). 폰과 연결되지 않았으면 [WatchNotice.PhoneNotConnected]("폰과 연결 필요")를 보인다.
     * 측정은 폰이 시작한다(C-002).
     */
    fun onStartTapped() {
        events.update { it.copy(startRequestedAtMillis = clock.millis(), notice = null) }
        viewModelScope.launch {
            if (!phoneLink.sendStartRequest()) {
                events.update {
                    it.copy(
                        startRequestedAtMillis = null,
                        notice = WatchNotice.PhoneNotConnected,
                        noticeAtMillis = clock.millis()
                    )
                }
            }
        }
    }

    /** OS 심박 권한 창이 닫혔거나 화면으로 돌아왔다. */
    fun onPermissionResult() = phoneLink.onHeartRatePermissionChanged()

    /** OS 심박 권한 창에 넘길 권한 이름. */
    val heartRatePermission: String get() = phoneLink.heartRatePermissionName

    /** 받은 시각은 PhoneLink가 찍은 값을 쓴다. 화면을 다시 만들 때 다시 받은 지난 표시를 새것으로 보지 않는다. */
    private fun onDisplay(display: Received) {
        received.value = display
        val phoneNotice = display.notice
        events.update { current ->
            when {
                phoneNotice != null ->
                    current.copy(
                        notice = WatchNotice.FromPhone(phoneNotice),
                        noticeAtMillis = display.atMillis,
                        startRequestedAtMillis = null
                    )

                display.running -> current.copy(startRequestedAtMillis = null)

                else -> current
            }
        }
    }

    private fun reduce(inputs: Inputs, now: Long): WatchUiState {
        val display = inputs.received
        val running = display?.running == true
        val allowed = display?.heartRateAllowed == true
        val freshLocal = inputs.latestHeartRate
            ?.takeIf { now - it.measuredAtEpochMillis <= HEART_RATE_STALE_MILLIS }
            ?.bpm
        val displayStale = display == null || now - display.atMillis > LINK_LOST_MILLIS
        val requestedAt = inputs.events.startRequestedAtMillis
        return WatchUiState(
            phoneConnected = inputs.connected,
            running = running,
            elapsedSeconds = display?.elapsedSeconds ?: 0,
            distanceMeters = display?.distanceMeters ?: 0,
            paceSecondsPerKm = display?.paceSecondsPerKm,
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

        private const val TICK_MILLIS = 1_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
