package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Outcome
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 폰 설정의 "못 올린 기록 n건, 다시 보내기"(W40). 서버가 거부해 자동 전송이 멈춘 기록(REJECTED, R12 D2-b)을 앱 결함을 고친 뒤
 * 다시 보낸다. ui.me가 부른다(R79: 거부 기록은 domain.run의 일이라 여기에 둔다. 그래서 ui.me → domain.run 화살표가 있다).
 * - 폰: [ResetRejectedRunsUseCase]로 미전송으로 되돌리고 전송을 깨운다.
 * - 워치: 워치에 같은 일을 요청한다([WatchResendRequester]). 폰에 되돌릴 기록이 없거나 폰 보관함이 실패해도 보낸다. 워치가 지금 닿지
 *   않아도 요청은 남았다가 연결되면 전달된다(W43).
 * 건수는 폰 건수와 워치가 알린 건수의 합이다(W43). 워치에만 거부 기록이 있어도 줄이 보여 다시 보낼 수 있다.
 * 다시 보내도 서버가 거부하면 다시 REJECTED로 남는다(기존 규칙).
 */
class ResendRejectedRunsUseCase
@Inject
constructor(
    private val pendingRuns: PendingRunRepository,
    private val resetRejected: ResetRejectedRunsUseCase,
    private val watch: WatchResendRequester
) {
    /** 못 올린 기록(REJECTED) 건수: 폰 건수(보관소가 센다, R80) + 워치 건수. 0이면 설정 화면이 줄을 보이지 않는다. */
    fun observeRejectedCount(): Flow<Int> = combine(
        pendingRuns.observeRejectedCount(),
        watch.observeWatchRejectedCount()
    ) { phone, onWatch -> phone + onWatch }
        .distinctUntilChanged()

    /** 폰에서 되돌린 건수. 폰 보관함을 바꾸지 못하면 실패다(워치 요청은 그래도 보낸다). */
    suspend operator fun invoke(): Outcome<Int> {
        val reset = resetRejected()
        watch.requestResend()
        return reset
    }
}
