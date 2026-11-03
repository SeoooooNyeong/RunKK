package com.a207.runkk.watch.link

import com.a207.runkk.app.domain.run.RunTracker
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 워치 측정 중 여부를 폰에 알린다(2.2, D-373 (2), D-390, D-391 M77). RunTracker의 측정 여부가 바뀔 때 보내고, 측정 중에는
 * [RESEND_MILLIS](30초, 제안)마다 참을 다시 보낸다. 워치 앱이 강제 종료되어 거짓을 보내지 못해도 폰은 마지막 참 뒤 90초가 지나면 풀린다(H-57).
 *
 * RunTracker에는 측정 여부 흐름이 없어 진행 알림 값(RunTracker.notice)으로 본다: 측정을 시작하면 첫 값을 내고, 끝나거나 취소되면
 * null이다(D-258). 앱을 막 띄웠을 때의 거짓은 보내지 않는다(측정한 적이 없으므로 폰이 막힐 일이 없다).
 */
@Singleton
class WatchMeasuringReporter @Inject constructor(
    private val runTracker: RunTracker,
    private val phoneLink: PhoneLink,
    private val scope: CoroutineScope
) {
    private val lock = Any()
    private var started = false

    /** 앱 프로세스가 시작될 때 한 번 부른다. 거듭 불러도 한 번만 돈다. */
    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        scope.launch {
            var hasMeasured = false
            runTracker.notice
                .map { it != null }
                .distinctUntilChanged()
                .collectLatest { isMeasuring ->
                    if (isMeasuring) {
                        hasMeasured = true
                        while (true) {
                            phoneLink.sendMeasuring(true)
                            delay(RESEND_MILLIS)
                        }
                    } else if (hasMeasured) {
                        phoneLink.sendMeasuring(false)
                    }
                }
        }
    }

    internal companion object {
        /** 측정 중 참을 다시 보내는 간격(D-390 제안 30초). 폰의 만료(90초)보다 짧아야 한다. */
        const val RESEND_MILLIS = 30_000L
    }
}
