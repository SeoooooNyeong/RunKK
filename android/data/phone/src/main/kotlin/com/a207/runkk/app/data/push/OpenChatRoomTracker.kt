package com.a207.runkk.app.data.push

import com.a207.runkk.app.domain.chat.OpenChatRoomRegistry
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 지금 열린 채팅방의 크루를 기억한다(6.1.14 data.push, FR-218, D-102, D-130). domain.chat의 OpenChatRoomRegistry를 구현하고
 * ChatRoom.open·close가 부른다. RunkkMessagingService가 앱 화면이 떠 있을 때 받은 푸시를 표시하기 전에 [shouldDisplay]로 묻는다:
 * 열린 방 크루의 CREW_CHAT 알림은 표시하지 않는다(폴링 모드에서는 서버가 방 입장을 몰라 방 안 크루원에게도 알림이 온다).
 * 같은 크루를 두 번 열고 한 번 닫으면 닫힌 것으로 본다(알림을 놓치는 쪽보다 한 번 더 보이는 쪽이 낫다).
 */
@Singleton
class OpenChatRoomTracker
@Inject
constructor() : OpenChatRoomRegistry {
    private val open: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    override fun opened(crewId: Long) {
        open += crewId
    }

    override fun closed(crewId: Long) {
        open -= crewId
    }

    /**
     * 푸시를 표시할지. [type]은 FCM data의 type(서버 NotificationType 이름), [collapseKey]는 서버 묶음 키("crew_chat_{크루 ID}",
     * NotificationService.notifyCollapsing)다. 열린 방 크루의 CREW_CHAT만 거짓이다.
     */
    fun shouldDisplay(type: String?, collapseKey: String?): Boolean {
        if (type != CREW_CHAT) return true
        val crewId = collapseKey?.removePrefix(CREW_CHAT_KEY_PREFIX)?.takeIf { it != collapseKey }?.toLongOrNull()
        return crewId == null || crewId !in open
    }

    private companion object {
        const val CREW_CHAT = "CREW_CHAT"

        /** 서버 묶음 키 = 종류 이름 소문자 + "_" + 대상 ID. */
        const val CREW_CHAT_KEY_PREFIX = "crew_chat_"
    }
}
