package com.a207.runkk.watch.heart

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 심박 센서 값을 모은다(6.1.15, FR-041).
 *
 * 수집은 세 조건이 모두 참일 때만 켠다: 폰의 허용 플래그([allow], WatchDisplayMessage.heartRateCollectionAllowed),
 * OS 심박 권한([SensorPermission]), 시작 요청([start]). 허용이 거짓으로 오면 다른 조건과 관계없이 즉시 센서를 끈다(NFR-023).
 * 처음 값은 "허용 없음"이라 폰이 허용을 보내기 전에는 수집하지 않는다.
 */
@Singleton
class HeartRateCollector @Inject constructor(
    private val sensor: HeartRateSensor,
    private val permission: SensorPermission,
    private val scope: CoroutineScope
) {
    /** 수집 상태. 우선순위는 위에서 아래(허용 없음이 가장 강하다). */
    enum class State {
        /** 폰이 수집을 허용하지 않았다(심박 동의 없음 또는 워치 연동 꺼짐). */
        BLOCKED,

        /** 허용은 있으나 OS 심박 권한이 없다. */
        NEEDS_PERMISSION,

        /** 허용·권한은 있으나 러닝 중이 아니다. */
        STOPPED,

        /** 센서를 켜고 값을 모으는 중. */
        COLLECTING,

        /** 센서를 쓸 수 없다(다른 앱의 운동 세션 등). stop 뒤 start로 다시 시도한다. */
        UNAVAILABLE
    }

    private val lock = Any()
    private var allowed = false
    private var requested = false
    private var sensorFailed = false
    private var job: Job? = null

    private val _state = MutableStateFlow(State.BLOCKED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _samples = MutableSharedFlow<WatchHeartRate>(extraBufferCapacity = SAMPLE_BUFFER)
    val samples: Flow<WatchHeartRate> = _samples.asSharedFlow()

    /** 폰이 보낸 허용 플래그. 거짓이면 즉시 멈춘다. */
    fun allow(allowed: Boolean) = update { this.allowed = allowed }

    /** 수집 시작 요청. 허용·권한이 없으면 상태만 남기고 조건이 갖춰질 때 켠다. 거듭 불러도 세션은 하나다. */
    fun start() = update {
        if (!requested) sensorFailed = false
        requested = true
    }

    fun stop() = update {
        requested = false
        sensorFailed = false
    }

    /** OS 권한 결과가 바뀌었을 때(권한 창 닫힘, 화면 복귀) 다시 확인한다. */
    fun onPermissionChanged() = update {}

    private inline fun update(change: () -> Unit) {
        synchronized(lock) {
            change()
            applyLocked()
        }
    }

    private fun applyLocked() {
        val next = when {
            !allowed -> State.BLOCKED
            !permission.granted() -> State.NEEDS_PERMISSION
            !requested -> State.STOPPED
            sensorFailed -> State.UNAVAILABLE
            else -> State.COLLECTING
        }
        if (next == State.COLLECTING) {
            if (job == null) job = launchCollection()
        } else {
            job?.cancel()
            job = null
        }
        _state.value = next
    }

    private fun launchCollection(): Job = scope.launch {
        try {
            sensor.heartRates().collect { _samples.emit(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 센서 오류(Health Services 등록 실패 등)는 아래에서 UNAVAILABLE로 알린다.
        }
        onSensorStopped(coroutineContext[Job])
    }

    /** 센서 흐름이 오류나 끝으로 멈췄다. 같은 요청 안에서는 다시 켜지 않는다. */
    private fun onSensorStopped(stopped: Job?) {
        synchronized(lock) {
            if (job !== stopped) return
            job = null
            sensorFailed = true
            applyLocked()
        }
    }

    private companion object {
        const val SAMPLE_BUFFER = 64
    }
}
