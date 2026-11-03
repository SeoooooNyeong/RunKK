package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import javax.inject.Inject

/**
 * 이 기기의 거부 기록(REJECTED)을 미전송으로 되돌리고 전송 작업을 바로 깨운다(W40). 폰은 설정의 "다시 보내기"
 * (ResendRejectedRunsUseCase)가, 워치는 폰의 다시 보내기 요청(WearPaths.RESEND_REJECTED)을 받은 watch.link가 부른다.
 * 되돌린 기록이 없으면 깨우지 않는다. 다시 보내도 서버가 거부하면 UploadPendingRunsUseCase가 다시 REJECTED로 남긴다(R12 D2-b).
 */
class ResetRejectedRunsUseCase
@Inject
constructor(
    private val pendingRuns: PendingRunRepository,
    private val uploadScheduler: UploadScheduler
) {
    /** 되돌린 건수. 보관함을 바꾸지 못하면 실패다. */
    suspend operator fun invoke(): Outcome<Int> {
        val reset = pendingRuns.resetRejected()
        if ((reset.getOrNull() ?: 0) > 0) uploadScheduler.requestUpload()
        return reset
    }
}
