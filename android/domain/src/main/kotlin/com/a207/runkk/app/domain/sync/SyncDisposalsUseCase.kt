package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.PendingRunRepository
import javax.inject.Inject

/**
 * 서버의 파기 지시를 기기 보관함에 적용한다(6.1.14). 앱 시작 때와 로그인 상태가 될 때 app의 RunkkApp이 부른다
 * (design-review.md E10, D-54). 기기에서 지우지 못한 지시는 확인을 보내지 않고 멈춘다(다음 동기화 때 다시).
 */
class SyncDisposalsUseCase
@Inject
constructor(
    private val pendingRuns: PendingRunRepository,
    private val remote: DisposalRemote
) {
    suspend operator fun invoke(): Outcome<Unit> {
        val orders =
            when (val pending = remote.pending()) {
                is Outcome.Failure -> return pending
                is Outcome.Success -> pending.value.iterator()
            }
        // 반복의 출구는 조건 하나다(리뷰 P-2): 실패한 지시에서 멈추고 그 실패를 돌려준다.
        var result: Outcome<Unit> = Outcome.Success(Unit)
        while (result is Outcome.Success && orders.hasNext()) result = apply(orders.next())
        return result
    }

    /** 지시 하나: 기기 사본을 지운 뒤 확인을 보낸다. 지우지 못하면 확인을 보내지 않는다. */
    private suspend fun apply(order: DisposalOrder): Outcome<Unit> {
        val purged =
            when (order.kind) {
                DisposalOrder.Kind.ROUTE -> pendingRuns.purgeRoutes()
                DisposalOrder.Kind.HEART_RATE -> pendingRuns.purgeHeartRates()
            }
        return if (purged is Outcome.Failure) purged else remote.confirm(order.orderId)
    }
}
