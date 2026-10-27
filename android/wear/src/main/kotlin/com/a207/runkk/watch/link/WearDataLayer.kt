package com.a207.runkk.watch.link

import kotlinx.coroutines.flow.Flow

/**
 * Wearable Data Layer 포트(D-20). 구현 [PlayServicesWearDataLayer]는 MessageClient·DataClient·NodeClient를 감싼다.
 * 실패는 예외 대신 거짓·빈 목록으로 돌려준다(PhoneLink가 다시 시도할지 정한다).
 */
interface WearDataLayer {
    /** 폰이 MessageClient로 보낸 메시지. 꺼내는 쪽은 하나(PhoneLink)다. */
    val messages: Flow<WearMessage>

    /** 지금 연결된 폰 노드 ID. 끊겼거나 조회에 실패하면 빈 목록. */
    suspend fun connectedNodeIds(): List<String>

    /** MessageClient 전송. 연결된 노드가 받았으면 참. */
    suspend fun sendMessage(nodeId: String, path: String, data: ByteArray): Boolean

    /** DataClient 저장. 끊겨 있어도 기기에 쌓였다가 연결되면 동기화된다(NFR-032). 저장되었으면 참. */
    suspend fun putData(path: String, data: ByteArray): Boolean
}
