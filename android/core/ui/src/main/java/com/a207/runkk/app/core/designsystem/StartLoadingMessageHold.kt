package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay

/**
 * 시작 로딩 문구가 보이는 시간 규칙(S15P31A207-345 2차, 순수 계산). 고른 문구([next]의 candidate)를 바로 따르지 않고:
 *
 * - 첫 문구는 로딩이 [appearDelayMillis] 넘게 이어질 때만 띄운다(받을 것이 없어 바로 넘어가면 문구를 띄우지 않는다).
 * - 띄운 문구는 [minShowMillis] 동안 유지한다(단계가 빨리 바뀌어도 깜빡이지 않는다). 그 뒤 고른 문구로 바꾼다.
 * - 고른 문구가 없어지면(준비 끝) 띄운 문구는 최소 시간까지 둔 뒤 거둔다.
 *
 * 시각은 밀리초 단조 시계(프레임 시각)이다.
 */
class StartLoadingMessageHold(
    private val minShowMillis: Long = MIN_SHOW_MILLIS,
    private val appearDelayMillis: Long = APPEAR_DELAY_MILLIS
) {
    /** 지금 보일 문구와, 다시 따져야 하는 때까지 남은 시간(null이면 고른 문구가 바뀔 때까지 그대로). */
    data class Decision(val shown: StartLoadingMessage?, val recheckAfterMillis: Long?)

    private var shown: StartLoadingMessage? = null
    private var shownAt = 0L
    private var startedAt: Long? = null

    fun next(candidate: StartLoadingMessage?, nowMillis: Long): Decision {
        val current = shown
        if (current == null) {
            if (candidate == null) return Decision(null, null)
            val start = startedAt ?: nowMillis.also { startedAt = it }
            val waited = nowMillis - start
            if (waited < appearDelayMillis) return Decision(null, appearDelayMillis - waited)
            shown = candidate
            shownAt = nowMillis
            return Decision(candidate, null)
        }
        if (candidate == current) return Decision(current, null)
        val visible = nowMillis - shownAt
        if (visible < minShowMillis) return Decision(current, minShowMillis - visible)
        shown = candidate
        shownAt = nowMillis
        return Decision(candidate, null)
    }

    companion object {
        /** 문구 최소 유지 시간(지휘자 결정 600ms). */
        const val MIN_SHOW_MILLIS = 600L

        /** 첫 문구를 띄우기 전 기다리는 시간. 캐시 적중으로 바로 넘어가는 시작(수십 ms)에는 문구가 번쩍이지 않는다. */
        const val APPEAR_DELAY_MILLIS = 300L
    }
}

/** [candidate]를 [StartLoadingMessageHold] 규칙(첫 등장 지연·최소 유지)으로 걸러 지금 보일 문구를 준다. 시각은 프레임 시각이다. */
@Composable
fun rememberStartLoadingMessage(candidate: StartLoadingMessage?): StartLoadingMessage? {
    val hold = remember { StartLoadingMessageHold() }
    var shown by remember { mutableStateOf<StartLoadingMessage?>(null) }
    LaunchedEffect(candidate) {
        while (true) {
            val decision = hold.next(candidate, withFrameMillis { it })
            shown = decision.shown
            delay(decision.recheckAfterMillis ?: break)
        }
    }
    return shown
}
