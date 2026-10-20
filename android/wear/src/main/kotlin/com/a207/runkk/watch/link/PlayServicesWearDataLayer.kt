package com.a207.runkk.watch.link

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Google Play 서비스 Wearable API로 [WearDataLayer]를 구현한다(D-20).
 * 시작 요청·표시 정보는 MessageClient, 심박 묶음은 DataClient(긴급 동기화), 연결 상태는 NodeClient.
 * Play 서비스 오류는 예외로 올리지 않고 거짓·빈 목록으로 바꾼다.
 */
class PlayServicesWearDataLayer @Inject constructor(@ApplicationContext context: Context) : WearDataLayer {
    private val messageClient = Wearable.getMessageClient(context)
    private val dataClient = Wearable.getDataClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    override val messages: Flow<WearMessage> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { event ->
            trySend(WearMessage(event.path, event.data))
        }
        messageClient.addListener(listener)
        awaitClose { messageClient.removeListener(listener) }
    }

    override suspend fun connectedNodeIds(): List<String> =
        attempt(emptyList()) { nodeClient.connectedNodes.await().map { it.id } }

    override suspend fun sendMessage(nodeId: String, path: String, data: ByteArray): Boolean = attempt(false) {
        messageClient.sendMessage(nodeId, path, data).await()
        true
    }

    override suspend fun putData(path: String, data: ByteArray): Boolean = attempt(false) {
        val request = PutDataRequest.create(path).setData(data).setUrgent()
        dataClient.putDataItem(request).await()
        true
    }

    /** 제한 시간 안에 끝나지 않거나 실패하면 [fallback]. 늦은 Play 서비스가 부르는 쪽을 붙잡지 않게 한다. */
    private suspend fun <T> attempt(fallback: T, block: suspend () -> T): T = try {
        withTimeoutOrNull(CALL_TIMEOUT_MILLIS) { block() } ?: fallback
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // ApiException(Play 서비스 없음·연결 끊김 등). 부르는 쪽(PhoneLink)이 다시 시도한다.
        fallback
    }

    private companion object {
        const val CALL_TIMEOUT_MILLIS = 10_000L
    }
}
