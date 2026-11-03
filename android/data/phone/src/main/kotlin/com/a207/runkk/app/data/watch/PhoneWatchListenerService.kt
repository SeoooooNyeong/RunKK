package com.a207.runkk.app.data.watch

import com.a207.runkk.wear.contract.WearPaths
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 워치의 로그인 정보 요청(SESSION_REQUEST)을 받는다(FR-240, FR-242, D-371, 코드 리뷰 AN12-1). 매니페스트에 경로를 등록한 서비스라
 * 폰 앱 프로세스가 죽어 있어도 워치 메시지가 프로세스를 띄운다. 받은 요청은 [WatchSessionPublisher.onSessionRequested]로 넘기고, 발행기는
 * 이미 보낸 회원이어도 새로 발급받아 요청한 워치에만 보낸다(워치가 세션을 잃은 경우의 복구 경로).
 * 발급은 서버 호출이라 앱 범위([scope])에서 한다. 이 경로만 요청을 받는다(앱 안 MessageClient 리스너와 둘 다 받으면 두 번 발급한다).
 */
@AndroidEntryPoint
class PhoneWatchListenerService : WearableListenerService() {
    @Inject
    lateinit var publisher: WatchSessionPublisher

    @Inject
    lateinit var scope: CoroutineScope

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearPaths.SESSION_REQUEST) return
        val nodeId = event.sourceNodeId
        scope.launch { publisher.onSessionRequested(nodeId) }
    }
}
