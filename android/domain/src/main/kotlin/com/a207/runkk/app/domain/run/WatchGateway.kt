package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** 워치 통신 포트. 구현 data.watch.WatchLink(D-20). */
interface WatchGateway {
    fun publish(d: WatchDisplay)

    val startRequests: Flow<Unit>

    val heartRates: Flow<List<HeartRateSample>>

    /** 워치의 종료 요청(길게 누름). RunTracker가 화면의 길게 누르기 종료와 같은 규칙으로 처리한다. 워치가 없으면 비어 있다. */
    val endRequests: Flow<Unit> get() = emptyFlow()

    fun isConnected(): Boolean
}
