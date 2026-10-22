package com.a207.runkk.watch.link

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 폰이 보낸 메시지를 받는 우편함. [PhoneMessageService](WearableListenerService)가 넣고 [PhoneLink]가 하나씩 꺼낸다.
 * 앱 프로세스가 죽어 있어도 폰 메시지가 프로세스를 다시 띄우므로, PhoneLink가 구독하기 전에 온 메시지를 버리지 않고 쌓아 둔다.
 */
@Singleton
class WearInbox @Inject constructor() {
    private val inbox = Channel<WearMessage>(capacity = CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 받은 순서대로. 꺼내는 쪽은 하나(PhoneLink)다. */
    val messages: Flow<WearMessage> = inbox.receiveAsFlow()

    /** 바인더 스레드에서 부른다. 기다리지 않고, 넘치면 가장 오래된 메시지를 버린다(표시 정보는 마지막 것이 중요하다). */
    fun deliver(message: WearMessage) {
        inbox.trySend(message)
    }

    private companion object {
        const val CAPACITY = 64
    }
}
