package com.a207.runkk.watch.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** WatchMode: 메인 카드 순서 자유런·배틀런·셀프런·셀럽런, 마지막 다음은 처음(순환, FR-243, D-375). 크루런 카드는 없다(W04). */
class WatchModeTest {
    @Test
    fun `카드는 네 장이고 크루런이 없다`() {
        assertEquals(listOf("FREE", "BATTLE", "SELF", "CELEB"), WatchMode.entries.map { it.name })
    }

    @Test
    fun `다음 카드는 순환한다`() {
        assertEquals(WatchMode.BATTLE, WatchMode.FREE.next())
        assertEquals(WatchMode.FREE, WatchMode.CELEB.next())
    }

    @Test
    fun `이전 카드도 순환한다`() {
        assertEquals(WatchMode.CELEB, WatchMode.FREE.previous())
        assertEquals(WatchMode.SELF, WatchMode.CELEB.previous())
    }
}
