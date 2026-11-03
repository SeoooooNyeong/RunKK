package com.a207.runkk.watch.link

import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.wear.contract.RejectedCountMessage
import com.a207.runkk.wear.contract.WearPaths
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 워치의 거부 기록(REJECTED) 건수를 폰에 알린다(W43, AN16-1). 보관함의 거부 건수(R80)를 구독해 건수가 바뀔 때마다
 * DataClient 항목(REJECTED_COUNT)에 넣는다. 항목은 폰이 끊겨 있어도 남았다가 연결되면 전달된다. 폰은 설정의 "못 올린 기록 n건"에 이 건수를 더해, 워치에만 거부 기록이
 * 있어도 다시 보내기를 누를 수 있다. 앱 프로세스가 뜰 때 지금 건수를 한 번 넣는다(0이어도: 옛 항목의 건수를 바로잡는다).
 */
@Singleton
class RejectedCountReporter @Inject constructor(
    private val pendingRuns: PendingRunRepository,
    private val dataLayer: WearDataLayer,
    private val clock: Clock,
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
            pendingRuns
                .observeRejectedCount()
                .distinctUntilChanged()
                .collect { count ->
                    val message = RejectedCountMessage(count, clock.millis())
                    if (!dataLayer.putData(WearPaths.REJECTED_COUNT, message.encode())) {
                        Timber.w("거부 기록 건수를 폰에 알리지 못했다")
                    }
                }
        }
    }
}
