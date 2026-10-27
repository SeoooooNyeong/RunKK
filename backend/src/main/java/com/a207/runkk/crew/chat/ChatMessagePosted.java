package com.a207.runkk.crew.chat;

import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;

/**
 * 채팅 메시지를 저장했다(설계 6.1.7a, D-101). 커밋 뒤 ChatDeliveryListener가 방 안 크루원에게 CrewChatPosted를, 방 밖 크루원에게
 * CREW_CHAT 알림을 보낸다.
 */
public record ChatMessagePosted(long crewId, long messageId, long authorId, LocalDateTime at)
        implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return at;
    }
}
