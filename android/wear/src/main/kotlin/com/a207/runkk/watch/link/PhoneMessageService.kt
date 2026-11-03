package com.a207.runkk.watch.link

import com.a207.runkk.watch.session.WatchSessionReceiver
import com.a207.runkk.wear.contract.WearPaths
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 폰 메시지를 받는다(D-20). 매니페스트에 등록한 서비스라 워치 앱 프로세스가 죽어 있어도 폰 메시지가 프로세스를 띄운다.
 * 프로세스가 뜨면 RunkkWatchApp → PhoneLink.start가 이전 프로세스가 남긴 운동 세션을 먼저 끝낸다(NFR-023).
 * 같은 메시지가 두 번 오지 않게 앱 안 MessageClient 리스너는 따로 두지 않는다.
 *
 * 로그인 경로(D-371): 로그인 정보(WATCH_SESSION, MessageClient)와 로그아웃 표시(SESSION_REVOKED, DataClient 항목)는 우편함·PhoneLink를
 * 거치지 않고 [sessionReceiver]로 바로 넘긴다(토큰이 다른 곳에 머물지 않게, NFR-050). 지운 항목(TYPE_DELETED: 폰이 새 로그인 정보를
 * 보낸 뒤 옛 표시를 지움, H-55)은 무시한다. 그 밖의 메시지는 우편함([inbox])에 넣어 PhoneLink가 꺼낸다.
 *
 * 거부 기록 다시 보내기 요청(RESEND_REJECTED, W40)은 [resendReceiver]로 바로 넘긴다(PhoneLink가 돌기 전에도 처리한다). 요청은
 * DataClient 항목으로 온다(W43: 워치가 연결되지 않았을 때 누른 요청도 연결되면 DATA_CHANGED로 온다). 옛 폰의 메시지도 받는다.
 */
@AndroidEntryPoint
class PhoneMessageService : WearableListenerService() {
    @Inject
    lateinit var inbox: WearInbox

    @Inject
    lateinit var sessionReceiver: WatchSessionReceiver

    @Inject
    lateinit var resendReceiver: RejectedResendReceiver

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearPaths.WATCH_SESSION -> sessionReceiver.onSession(event.data)
            WearPaths.RESEND_REJECTED -> resendReceiver.onRequest(event.data)
            else -> inbox.deliver(WearMessage(event.path, event.data))
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // 버퍼는 이 호출이 끝나면 풀리므로 본문을 여기서 읽어 넘긴다(수신기는 해독한 값만 쥔다).
        onDataEvents(dataEvents)
    }

    /** 바뀐 DataClient 항목 가운데 로그아웃 표시와 다시 보내기 요청만 넘긴다. 지운 항목은 무시한다. */
    internal fun onDataEvents(events: Iterable<DataEvent>) {
        events
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .forEach { event ->
                val path = event.dataItem.uri.path
                val data = event.dataItem.data ?: return@forEach
                when {
                    isSessionRevoked(path) -> sessionReceiver.onRevoked(data)
                    path == WearPaths.RESEND_REJECTED -> resendReceiver.onRequest(data)
                }
            }
    }

    /** SESSION_REVOKED 경로 또는 그 아래 경로(폰이 회원별 하위 경로를 쓰더라도 받는다). */
    private fun isSessionRevoked(path: String?): Boolean =
        path == WearPaths.SESSION_REVOKED || path?.startsWith("${WearPaths.SESSION_REVOKED}/") == true
}
