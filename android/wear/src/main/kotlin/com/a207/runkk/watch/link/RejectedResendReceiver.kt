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
 * 요청은 DataClient 항목으로 오고(W43: 워치가 연결되지 않았을 때 누른 요청도 연결되면 온다), 옛 폰의 MessageClient 요청도 받는다.
 * 직전에 처리한 요청 시각과 같으면 다시 처리하지 않는다(항목과 메시지로 함께 오거나 항목이 다시 동기화돼도). 프로세스가 새로 떠서 같은 요청을 다시 받아도
 * 되돌리기는 REJECTED만 바꾸므로 결과가 같다. 해독할 수 없는 본문은 버리고 경고만 남긴다(F.4.4). 다시 보내도 서버가 거부하면 다시
 * REJECTED로 남는다.
 */
@Singleton
class RejectedResendReceiver @Inject constructor(
    private val resetRejected: ResetRejectedRunsUseCase,
    private val scope: CoroutineScope
) {
    private val lock = Any()

    /** 직전에 처리한 요청 시각. 폰 시계가 거꾸로 가도 새 요청을 놓치지 않게 크기가 아니라 같음으로 본다. lock 안에서만 쓴다. */
    private var lastRequestedAt: Long? = null

    fun onRequest(bytes: ByteArray) {
        val request =
            try {
                ResendRejectedMessage.decode(bytes)
            } catch (e: SerializationException) {
                Timber.w("해독할 수 없는 다시 보내기 요청을 버린다")
                return
            } catch (e: IllegalArgumentException) {
                Timber.w("해독할 수 없는 다시 보내기 요청을 버린다")
                return
            }
        val isNew =
            synchronized(lock) {
                (lastRequestedAt != request.requestedAtEpochMillis).also { fresh ->
                    if (fresh) lastRequestedAt = request.requestedAtEpochMillis
                }
            }
        if (!isNew) return
        scope.launch {
            val reset = resetRejected()
            if (reset is Outcome.Failure) Timber.w("거부 기록을 되돌리지 못했다: %s", reset.error)
        }
    }
}
