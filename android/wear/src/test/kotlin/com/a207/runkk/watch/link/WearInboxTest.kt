package com.a207.runkk.watch.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** 폰 메시지 우편함: 폰 메시지가 프로세스를 띄운 직후 PhoneLink가 구독하기 전에 온 메시지도 버리지 않는다(D-20). */
@OptIn(ExperimentalCoroutinesApi::class)
class WearInboxTest {
    private fun message(index: Int) = WearMessage("/runkk/watch-display", byteArrayOf(index.toByte()))

    @Test
    fun `구독 전에 온 메시지를 받은 순서대로 넘긴다`() = runTest {
        val inbox = WearInbox()
        inbox.deliver(message(1))
        inbox.deliver(message(2))

        val received = mutableListOf<Int>()
        backgroundScope.launch { inbox.messages.collect { received += it.data.single().toInt() } }
        runCurrent()
        inbox.deliver(message(3))
        runCurrent()

        assertEquals(listOf(1, 2, 3), received)
    }

    @Test
    fun `넘치면 가장 오래된 메시지부터 버리고 최근 메시지는 남긴다`() = runTest {
        val inbox = WearInbox()
        repeat(100) { inbox.deliver(message(it)) }

        val received = mutableListOf<Int>()
        backgroundScope.launch { inbox.messages.collect { received += it.data.single().toInt() } }
        runCurrent()

        assertEquals(64, received.size)
        assertEquals(99, received.last())
    }
}
