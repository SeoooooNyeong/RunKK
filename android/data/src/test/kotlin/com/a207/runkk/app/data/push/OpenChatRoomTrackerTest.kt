package com.a207.runkk.app.data.push

import kotlin.test.Test
import kotlin.test.assertEquals

/** OpenChatRoomTracker(TC-6501, TC-6502, FR-218, D-102, D-130): 열린 방 크루의 CREW_CHAT 푸시만 표시하지 않는다. */
class OpenChatRoomTrackerTest {
    private val tracker = OpenChatRoomTracker()

    @Test
    fun `TC-6501 열린 방 크루의 채팅 알림만 생략한다`() {
        tracker.opened(5)

        assertEquals(
            listOf(false, true, true),
            listOf(
                tracker.shouldDisplay("CREW_CHAT", "crew_chat_5"),
                tracker.shouldDisplay("CREW_CHAT", "crew_chat_6"),
                tracker.shouldDisplay("CREW_NOTICE", null)
            )
        )
    }

    @Test
    fun `TC-6502 방을 닫은 뒤의 채팅 알림은 표시한다`() {
        tracker.opened(5)
        tracker.closed(5)

        assertEquals(true, tracker.shouldDisplay("CREW_CHAT", "crew_chat_5"))
    }

    @Test
    fun `묶음 키가 없거나 모양이 다르면 표시한다`() {
        tracker.opened(5)

        assertEquals(true, tracker.shouldDisplay("CREW_CHAT", null))
        assertEquals(true, tracker.shouldDisplay("CREW_CHAT", "5"))
        assertEquals(true, tracker.shouldDisplay("CREW_CHAT", "crew_chat_x"))
    }
}
