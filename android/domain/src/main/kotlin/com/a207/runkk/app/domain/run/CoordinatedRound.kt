package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * 대결·크루런 조정자가 함께 쓰는 "한 회"의 기반(리뷰 S-2). 두 조정자(domain.battle·domain.crew)가 같은 모듈 안에서만 쓴다.
 * - 범위: applicationScope 아래의 자식 범위(SupervisorJob + 직렬 디스패처). 범위 안의 처리되지 않은 예외는 onFailure로 넘긴다(S-8, S-9).
 * - 측정 여부: 원자적으로 바꿔 이전 회 정리와 이 회의 종료가 겹쳐도 endRun을 한 번만 부른다(O-1).
 * - 진행값: RunTracker.progress 구독과 화면의 onProgress가 같은 값을 두 번 처리하지 않게 경과 시간이 늘어난 값만 받는다(S-16).
 * - 서버 보고: 네트워크·인증 실패는 1·2초 뒤 두 번 더 보내고, 거부(Rejected)는 다시 보내지 않는다(F-3).
 */
internal class CoordinatedRound(
    parent: CoroutineScope,
    private val tracker: RunTracker,
    onFailure: (Throwable) -> Unit
) {
    val scope: CoroutineScope
    private val measuring = AtomicBoolean(false)

    /** 마지막으로 처리한 진행값의 경과 시간. scope의 직렬 디스패처에서만 읽고 쓴다. */
    private var lastElapsed: Duration = Duration.ZERO

    init {
        val context = parent.coroutineContext
        val base =
            requireNotNull(context[ContinuationInterceptor] as? CoroutineDispatcher) {
                "applicationScope has no dispatcher"
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        val serial = base.limitedParallelism(1)
        val handler = CoroutineExceptionHandler { _, error -> onFailure(error) }
        scope = CoroutineScope(context + SupervisorJob(context.job) + serial + handler)
    }

    val isMeasuring: Boolean
        get() = measuring.get()

    /** 측정을 시작하고 진행값 구독을 붙인다. 시작이 거부되면 그 실패를 돌려준다. */
    fun begin(spec: RunStartSpec, onProgress: (RunProgress) -> Unit): Outcome<Unit> {
        val started = tracker.startRun(spec)
        if (started is Outcome.Failure) return started
        measuring.set(true)
        scope.launch { tracker.progress.filter { it.elapsed > Duration.ZERO }.collect(onProgress) }
        return started
    }

    /** 처음 보는(경과 시간이 늘어난) 진행값이면 참. 직렬 디스패처에서만 부른다. */
    fun accept(p: RunProgress): Boolean {
        if (p.elapsed <= lastElapsed) return false
        lastElapsed = p.elapsed
        return true
    }

    /** 측정 중이면 endRun(기록 저장). 여러 곳에서 동시에 불러도 한 번만 끝낸다. */
    fun endMeasuring() {
        if (measuring.compareAndSet(true, false)) tracker.endRun()
    }

    fun cancel() {
        scope.cancel()
    }

    /** 서버 보고(시작·그만두기). 다시 보낼 만한 실패면 1·2초 뒤 두 번 더 보낸다. */
    suspend fun report(call: suspend () -> Outcome<Unit>): Outcome<Unit> {
        var result = call()
        var attempt = 1
        while (result.isRetryable() && attempt < REPORT_ATTEMPTS) {
            delay(REPORT_RETRY_MILLIS * attempt)
            result = call()
            attempt++
        }
        return result
    }

    private fun Outcome<Unit>.isRetryable(): Boolean = this is Outcome.Failure && error !is AppError.Rejected

    private companion object {
        const val REPORT_ATTEMPTS = 3
        const val REPORT_RETRY_MILLIS = 1_000L
    }
}
