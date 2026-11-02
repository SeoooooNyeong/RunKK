package com.a207.runkk.watch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.watch.link.PhoneLeadChange
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
import kotlinx.coroutines.flow.first
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
class WatchViewModel @Inject constructor(
    private val phoneLink: PhoneLink,
    private val clock: Clock,
    private val notificationGate: NotificationPermissionGate
) : ViewModel() {
    /** 폰 표시 정보 중 화면이 쓰는 값(WatchDisplayMessage에서 옮김)과 받은 시각. */
    private data class Received(
        val isRunning: Boolean,
        val elapsedSeconds: Long,
        val distanceMeters: Long,
        val paceSecondsPerKm: Int?,
        val heartRateBpm: Int?,
        val planDistanceMeters: Long?,
        val planPaceSecondsPerKm: Int?,
        val isHeartRateAllowed: Boolean,
        val notice: String?,
        val isPaused: Boolean,
        val isPausable: Boolean,
        val atMillis: Long
    )

    /** 워치가 마지막으로 잰 정상 심박. */
    private data class LocalHeartRate(val measuredAtEpochMillis: Long, val bpm: Int)

    private data class LocalEvents(
        val notice: WatchNotice? = null,
        val noticeAtMillis: Long = 0,
        val isNotificationPromptPending: Boolean = false
    )

    /** 마지막으로 받은 근접 알림(PhoneProximity에서 옮김)과 받은 시각. */
    private data class Proximity(val gapMeters: Int, val isAhead: Boolean, val intervalMillis: Long, val atMillis: Long)

    private data class Inputs(
        val received: Received?,
        val isConnected: Boolean,
        val isPermissionNeeded: Boolean,
        val latestHeartRate: LocalHeartRate?,
        val events: LocalEvents,
        val proximity: Proximity? = null,
        val leadChange: PhoneLeadChange? = null
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
        events
    ) { display, isConnected, isPermissionNeeded, heartRate, localEvents ->
        Inputs(display, isConnected, isPermissionNeeded, heartRate, localEvents)
    }
        .combine(
            phoneLink.proximity.map { received ->
                received?.let { Proximity(it.gapMeters, it.isAhead, it.intervalMillis, it.receivedAtMillis) }
            }
        ) { inputs, proximity -> inputs.copy(proximity = proximity) }
        .combine(phoneLink.leadChange) { inputs, lead -> inputs.copy(leadChange = lead) }
        .combine(ticker) { inputs, _ -> reduce(inputs, clock.millis()) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WatchUiState())

    init {
        viewModelScope.launch {
            phoneLink.displays.collect { display ->
                onDisplay(
                    Received(
                        isRunning = display.isRunning,
                        elapsedSeconds = display.elapsedSeconds,
                        distanceMeters = display.distanceMeters,
                        paceSecondsPerKm = display.currentPaceSecondsPerKm,
                        heartRateBpm = display.heartRateBpm,
                        planDistanceMeters = display.planDistanceMeters,
                        planPaceSecondsPerKm = display.planPaceSecondsPerKm,
                        isHeartRateAllowed = display.isHeartRateCollectionAllowed,
                        notice = display.notice,
                        isPaused = display.isPaused,
                        isPausable = display.isPausable,
                        atMillis = phoneLink.lastDisplayAtMillis.value ?: clock.millis()
                    )
                )
            }
        }
        viewModelScope.launch {
            phoneLink.heartRateCollecting.first { collecting -> collecting }
            if (notificationGate.shouldAsk()) events.update { it.copy(isNotificationPromptPending = true) }
        }
    }

    /** 종료 버튼을 짧게 눌렀다(FR-181). 요청은 보내지 않고 "길게 눌러 종료"를 안내한다. */
    fun onEndTapped() {
        events.update { it.copy(notice = WatchNotice.HoldToEnd, noticeAtMillis = clock.millis()) }
    }

    /**
     * 종료 버튼을 길게 눌렀다(FR-181). 폰에 종료 요청을 보낸다. 끝내는 것은 폰이고(C-002), 폰과 연결되어 있지 않으면
     * [WatchNotice.PhoneNotConnected]를 보인다. 대결·크루런 거절은 폰이 표시 정보의 안내로 돌려보낸다.
     */
    fun onEndLongPressed() {
        events.update { it.copy(notice = null) }
        viewModelScope.launch {
            if (!phoneLink.sendEndRequest()) {
                events.update { it.copy(notice = WatchNotice.PhoneNotConnected, noticeAtMillis = clock.millis()) }
            }
        }
    }

    /**
     * 일시정지 버튼(FR-194). 폰에 요청만 보내고 정지는 폰이 한다(D-93). 정지 표시는 폰의 다음 표시 정보(isPaused)로 바뀐다.
     * 폰과 연결되어 있지 않으면 [WatchNotice.PhoneNotConnected]를 보인다.
     */
    fun onPauseTapped() = sendPauseRequest { phoneLink.requestPause() }

    /** 재개 버튼(FR-194). [onPauseTapped]와 같다. */
    fun onResumeTapped() = sendPauseRequest { phoneLink.requestResume() }

    private fun sendPauseRequest(send: suspend () -> Boolean) {
        events.update { it.copy(notice = null) }
        viewModelScope.launch {
            if (!send()) {
                events.update { it.copy(notice = WatchNotice.PhoneNotConnected, noticeAtMillis = clock.millis()) }
            }
        }
    }

    /** 알림 권한 창을 띄웠다. 결과(허용·거절)와 관계없이 다시 묻지 않고, 심박 수집은 계속한다. */
    fun onNotificationPromptShown() {
        notificationGate.markAsked()
        events.update { it.copy(isNotificationPromptPending = false) }
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
                    current.copy(notice = WatchNotice.FromPhone(phoneNotice), noticeAtMillis = display.atMillis)

                else -> current
            }
        }
    }

    private fun reduce(inputs: Inputs, now: Long): WatchUiState {
        val display = inputs.received
        val isRunning = display?.isRunning == true
        val isAllowed = display?.isHeartRateAllowed == true
        val freshLocal = inputs.latestHeartRate
            ?.takeIf { now - it.measuredAtEpochMillis <= HEART_RATE_STALE_MILLIS }
            ?.bpm
        val displayStale = display == null || now - display.atMillis > LINK_LOST_MILLIS
        return WatchUiState(
            isPhoneConnected = inputs.isConnected,
            isRunning = isRunning,
            elapsedSeconds = display?.elapsedSeconds ?: 0,
            distanceMeters = display?.distanceMeters ?: 0,
            paceSecondsPerKm = display?.paceSecondsPerKm,
            heartRateBpm = if (isAllowed) freshLocal ?: display?.heartRateBpm else null,
            planDistanceMeters = display?.planDistanceMeters,
            planPaceSecondsPerKm = display?.planPaceSecondsPerKm,
            isLinkLost = isRunning && (!inputs.isConnected || displayStale),
            isHeartRatePermissionNeeded = inputs.isPermissionNeeded,
            notice = inputs.events.notice?.takeIf { now - inputs.events.noticeAtMillis < NOTICE_MILLIS },
            isNotificationPermissionNeeded = inputs.events.isNotificationPromptPending,
            isPaused = isRunning && display?.isPaused == true,
            isPausable = isRunning && display?.isPausable == true,
            proximity = inputs.proximity
                ?.takeIf { isRunning && now - it.atMillis < PROXIMITY_EXPIRY_MILLIS }
                ?.let { WatchProximity(it.gapMeters, it.isAhead, it.intervalMillis) },
            leadKind = inputs.leadChange
                ?.takeIf { isRunning && now - it.receivedAtMillis < LEAD_BANNER_MILLIS }
                ?.let { if (it.isOvertook) LeadKind.OVERTOOK else LeadKind.OVERTAKEN }
        )
    }

    internal companion object {
        /** 이보다 오래된 심박은 빈칸(6.1.15). */
        const val HEART_RATE_STALE_MILLIS = 30_000L

        /** 러닝 중 폰 표시가 이만큼 없으면 연결 끊김으로 보인다(폰은 1초마다 보낸다). */
        const val LINK_LOST_MILLIS = 5_000L

        /** 안내 문구를 보이는 시간. */
        const val NOTICE_MILLIS = 5_000L

        /** 근접 알림 화면을 남기는 시간. 소리·진동처럼 마지막 메시지 뒤 3초가 되면(정각 포함) 내린다(D-158). */
        const val PROXIMITY_EXPIRY_MILLIS = 3_000L

        /** "역전!" 띠를 두는 시간(워치 단독 측정과 같다, WatchFlowViewModel.LEAD_BANNER_MILLIS). */
        const val LEAD_BANNER_MILLIS = 2_000L

        private const val TICK_MILLIS = 1_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
