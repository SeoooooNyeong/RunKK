package com.a207.runkk.watch.link

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 폰 메시지를 받는다(D-20). 매니페스트에 등록한 서비스라 워치 앱 프로세스가 죽어 있어도 폰 메시지가 프로세스를 띄운다.
 * 프로세스가 뜨면 RunkkWatchApp → PhoneLink.start가 이전 프로세스가 남긴 운동 세션을 먼저 끝낸다(NFR-023).
 * 같은 메시지가 두 번 오지 않게 앱 안 MessageClient 리스너는 따로 두지 않는다.
 */
@AndroidEntryPoint
class PhoneMessageService : WearableListenerService() {
    @Inject
    lateinit var inbox: WearInbox

    override fun onMessageReceived(event: MessageEvent) {
        inbox.deliver(WearMessage(event.path, event.data))
    }
}
