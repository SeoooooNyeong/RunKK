package com.a207.runkk.app.ui.run

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 3초 시작 카운트다운(6.1.14, FR-180). 숫자를 누르면 [skip]으로 바로 시작한다.
 * 대결·크루런은 서버 시작일시로 자동 시작해 쓰지 않는다. [onFinish]는 한 번만 부른다.
 */
class StartCountdown(private val scope: CoroutineScope, private val seconds: Int = DEFAULT_SECONDS) {
    private var job: Job? = null
    private var finish: (() -> Unit)? = null

    fun begin(onTick: (Int) -> Unit, onFinish: () -> Unit) {
        job?.cancel()
        finish = onFinish
        job = scope.launch {
            for (n in seconds downTo 1) {
                onTick(n)
                delay(TICK_MILLIS)
            }
            complete()
        }
    }

    /** 바로 시작. 이미 끝났거나 시작 전이면 아무것도 하지 않는다. */
    fun skip() {
        job?.cancel()
        complete()
    }

    /** 시작하지 않고 멈춘다. */
    fun cancel() {
        job?.cancel()
        finish = null
    }

    private fun complete() {
        val callback = finish ?: return
        finish = null
        callback()
    }

    private companion object {
        const val DEFAULT_SECONDS = 3
        const val TICK_MILLIS = 1_000L
    }
}
