package com.a207.runkk.watch.link

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.ResetRejectedRunsUseCase
import com.a207.runkk.wear.contract.ResendRejectedMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import timber.log.Timber

/**
 * 폰의 거부 기록 다시 보내기 요청(WearPaths.RESEND_REJECTED, W40)을 받아 워치 보관함의 거부 기록(REJECTED)을 미전송으로 되돌리고
 * 전송을 깨운다([ResetRejectedRunsUseCase]). [PhoneMessageService]가 경로로 골라 부른다(바인더 스레드라 해독만 하고 바로 돌아온다).
 * 해독할 수 없는 본문은 버리고 경고만 남긴다(F.4.4). 다시 보내도 서버가 거부하면 다시 REJECTED로 남는다.
 */
@Singleton
class RejectedResendReceiver @Inject constructor(
    private val resetRejected: ResetRejectedRunsUseCase,
    private val scope: CoroutineScope
) {
    fun onRequest(bytes: ByteArray) {
        try {
            ResendRejectedMessage.decode(bytes)
        } catch (e: SerializationException) {
            Timber.w("해독할 수 없는 다시 보내기 요청을 버린다")
            return
        } catch (e: IllegalArgumentException) {
            Timber.w("해독할 수 없는 다시 보내기 요청을 버린다")
            return
        }
        scope.launch {
            val reset = resetRejected()
            if (reset is Outcome.Failure) Timber.w("거부 기록을 되돌리지 못했다: %s", reset.error)
        }
    }
}
