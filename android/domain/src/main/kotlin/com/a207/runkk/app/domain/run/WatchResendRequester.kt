package com.a207.runkk.app.domain.run

import kotlinx.coroutines.flow.Flow

/**
 * 연결된 워치의 거부 기록을 다루는 포트(W40, W43). 구현 data.watch.WatchLink. ResendRejectedRunsUseCase만 부른다.
 * - [requestResend]: 워치에 거부 기록 다시 보내기를 요청한다(WearPaths.RESEND_REJECTED, DataClient 항목). 워치가 지금 닿지 않아도
 *   요청은 남았다가 연결되면 전달된다.
 * - [observeWatchRejectedCount]: 워치가 알린 자기 거부 기록 건수(WearPaths.REJECTED_COUNT). 워치가 연결되지 않았거나 워치 연동이
 *   꺼져 있으면 0이다.
 */
interface WatchResendRequester {
    suspend fun requestResend()

    fun observeWatchRejectedCount(): Flow<Int>
}
