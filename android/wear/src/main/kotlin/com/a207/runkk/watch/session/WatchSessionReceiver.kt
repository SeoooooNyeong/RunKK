package com.a207.runkk.watch.session

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.wear.contract.SessionRevokedMessage
import com.a207.runkk.wear.contract.WatchSessionMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import timber.log.Timber

/**
 * 폰이 보낸 로그인 정보·로그아웃 표시를 [WatchSessionStore]에 넘긴다(3.3, D-389). watch.link의 PhoneMessageService가 경로로 골라 부른다.
 *
 * 바인더 스레드에서 불리므로 해독만 하고 바로 돌아온다. 처리는 받은 순서대로 하나씩 한다(로그인 정보 뒤 로그아웃 표시가 뒤바뀌지 않게).
 * 로그아웃 표시는 처리하는 순간 측정 중이면 deferRevoke, 아니면 revoke를 고른다(R68). 해독할 수 없는 본문은 버리고 경고만 남긴다(F.4.4).
 * 토큰 값은 로그에 쓰지 않는다(WatchSessionMessage.toString이 가린다, NFR-050).
 */
@Singleton
class WatchSessionReceiver @Inject constructor(
    private val store: WatchSessionStore,
    private val runTracker: RunTracker,
    scope: CoroutineScope
) {
    private val work = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (job in work) {
                try {
                    job()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RuntimeException) {
                    Timber.e(e, "로그인 정보 처리 실패")
                }
            }
        }
    }

    fun onSession(bytes: ByteArray) {
        val message = decodeOrNull("로그인 정보") { WatchSessionMessage.decode(bytes) } ?: return
        work.trySend {
            val accepted = store.accept(message)
            if (accepted is Outcome.Failure) Timber.w("로그인 정보를 받지 못했다: %s", accepted.error)
        }
    }

    fun onRevoked(bytes: ByteArray) {
        val message = decodeOrNull("로그아웃 표시") { SessionRevokedMessage.decode(bytes) } ?: return
        work.trySend {
            val revoked = if (runTracker.isMeasuring) store.deferRevoke(message) else store.revoke(message)
            if (revoked is Outcome.Failure) Timber.w("로그아웃 표시를 처리하지 못했다: %s", revoked.error)
        }
    }

    /** 해독 실패는 예외 종류만 남긴다. 예외 메시지에 본문 조각(토큰)이 실릴 수 있다(NFR-050, 코드 리뷰 WR12-5). */
    private inline fun <T> decodeOrNull(what: String, decode: () -> T): T? = try {
        decode()
    } catch (e: SerializationException) {
        Timber.w("%s 본문을 해독하지 못해 버린다(%s)", what, e::class.simpleName)
        null
    } catch (e: IllegalArgumentException) {
        Timber.w("%s 본문을 해독하지 못해 버린다(%s)", what, e::class.simpleName)
        null
    }
}
