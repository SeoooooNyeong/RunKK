package com.a207.runkk.watch.alert

import com.a207.runkk.wear.contract.LeadChangeMessage
import com.a207.runkk.wear.contract.ProximityMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 대결 근접 알림을 소리·진동으로 재생한다(6.1.15 watch.alert, FR-196, FR-188, D-96).
 *
 * - [onProximity]: 켜짐이면 폰이 준 intervalMillis 간격으로 비프음과 짧은 진동을 반복한다. 간격은 계산하지 않는다(폰 ProximityAlertRule).
 *   새 메시지가 오면 간격을 바꾸고, 다음 비프는 마지막 비프 + 새 간격(이미 지났으면 바로)이다. 꺼짐이면 바로 멈춘다.
 * - 마지막 메시지 뒤 [EXPIRY_MILLIS]가 되는 정각(경과 ≥ 3초)에 스스로 멈춘다(D-158). 폰은 켜져 있는 동안 2초마다 다시 보낸다.
 * - [onLeadChange]: 추월·역전이면 1초 긴 진동을 곧바로 한 번 낸다. 반복 중이면 긴 진동이 끝날 때까지 비프를 미루고 끝난 시각부터 잇는다.
 *   반복 중이 아니면 반복을 시작하지 않는다.
 * - [stop]: 러닝이 끝났다는 표시(isRunning 거짓)를 받으면 PhoneLink가 부른다.
 *
 * 멈출 때(끔·만료·[stop])는 비프음 자원을 놓는다. 시각은 모두 단조 시계([MonotonicClock])로 잰다: 러닝 중 벽시계가 바뀌어도
 * 만료·간격이 흔들리지 않는다.
 *
 * 부르는 쪽은 여러 스레드일 수 있어 상태는 [lock] 안에서만 바꾼다. 반복은 [scope]의 코루틴 하나가 맡고, 상태가 바뀔 때마다 새로 시작한다.
 */
@Singleton
class ProximityAlertPlayer @Inject constructor(
    private val vibrator: AlertVibrator,
    private val beeper: AlertBeeper,
    private val clock: MonotonicClock,
    private val scope: CoroutineScope
) {
    private val lock = Any()
    private var intervalMillis = 0L
    private var lastMessageAtMillis = 0L
    private var lastBeatAtMillis: Long? = null

    /** 추월·역전 긴 진동이 끝나는 시각. 그 전에는 비프를 내지 않는다. */
    private var holdUntilMillis = Long.MIN_VALUE
    private var loop: Job? = null

    /** 반복을 새로 시작하거나 멈출 때마다 늘린다. 기다리다 깨어난 옛 반복이 소리를 내지 않게 한다. */
    private var generation = 0L

    private val _isPlaying = MutableStateFlow(false)

    /** 근접 알림을 반복하는 중인지. */
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** 폰의 근접 알림 메시지(PROXIMITY). */
    fun onProximity(message: ProximityMessage) {
        synchronized(lock) {
            if (!message.isActive) {
                stopLocked()
                return
            }
            intervalMillis = message.intervalMillis.coerceIn(MIN_INTERVAL_MILLIS, EXPIRY_MILLIS)
            lastMessageAtMillis = clock.elapsedMillis()
            restartLocked()
        }
    }

    /** 폰의 추월·역전 메시지(LEAD_CHANGE). 종류와 관계없이 1초 긴 진동 한 번이다. */
    fun onLeadChange(message: LeadChangeMessage) {
        Timber.d("추월·역전 %s", message.kind)
        synchronized(lock) {
            emit { vibrator.vibrate(LEAD_CHANGE_VIBRATION_MILLIS) }
            holdUntilMillis = clock.elapsedMillis() + LEAD_CHANGE_VIBRATION_MILLIS
            if (_isPlaying.value) restartLocked()
        }
    }

    /** 반복을 멈춘다. 거듭 불러도 된다. */
    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    private fun restartLocked() {
        loop?.cancel()
        _isPlaying.value = true
        val current = ++generation
        loop = scope.launch { play(current) }
    }

    private fun stopLocked() {
        loop?.cancel()
        loop = null
        generation++
        lastBeatAtMillis = null
        _isPlaying.value = false
        emit { beeper.release() }
    }

    private suspend fun play(current: Long) {
        while (true) {
            val now = clock.elapsedMillis()
            val step = synchronized(lock) { nextStepLocked(now) }
            delay(step.atMillis - now)
            synchronized(lock) {
                // 기다리는 동안 새 메시지·멈춤으로 다른 반복이 시작되었으면 아무것도 하지 않는다.
                if (current != generation) return
                if (step.isExpiry) {
                    stopLocked()
                    return
                }
                emit { beeper.beep(BEEP_MILLIS) }
                emit { vibrator.vibrate(BEAT_VIBRATION_MILLIS) }
                lastBeatAtMillis = clock.elapsedMillis()
            }
        }
    }

    /**
     * 진동기·비프음 호출. 기기 오류(RuntimeException)는 기록만 하고 삼킨다: 반복 코루틴이 죽거나
     * [onLeadChange]를 부른 PhoneLink 수신 코루틴으로 예외가 새어 폰 메시지를 더 받지 못하게 되지 않게 한다.
     */
    private inline fun emit(output: () -> Unit) {
        try {
            output()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            Timber.w(e, "근접 알림 소리·진동을 내지 못했다")
        }
    }

    private fun nextStepLocked(now: Long): Step {
        val nextBeat = maxOf(lastBeatAtMillis?.plus(intervalMillis) ?: now, holdUntilMillis, now)
        val expiry = lastMessageAtMillis + EXPIRY_MILLIS
        return if (nextBeat >= expiry) Step(maxOf(expiry, now), isExpiry = true) else Step(nextBeat, isExpiry = false)
    }

    private data class Step(val atMillis: Long, val isExpiry: Boolean)

    internal companion object {
        /** 마지막 근접 메시지 뒤 이만큼 지나면(정각 포함) 멈춘다(F.4.2 제안 3초, D-158). */
        const val EXPIRY_MILLIS = 3_000L

        /** 추월·역전 긴 진동 길이(FR-188, FR-196). */
        const val LEAD_CHANGE_VIBRATION_MILLIS = 1_000L

        /** 반복 한 번의 짧은 진동·비프 길이. 가장 짧은 간격(300ms)보다 짧다. */
        const val BEAT_VIBRATION_MILLIS = 60L
        const val BEEP_MILLIS = 80

        /**
         * 잘못된 간격(0 이하 등)으로 쉬지 않고 울리지 않게 하는 하한. 폰의 가장 짧은 간격은 300ms다(F.4.3).
         * 상한은 [EXPIRY_MILLIS]다(그 이상이면 어차피 한 번 울리고 만료된다. 덧셈 넘침도 막는다).
         */
        const val MIN_INTERVAL_MILLIS = 100L
    }
}
