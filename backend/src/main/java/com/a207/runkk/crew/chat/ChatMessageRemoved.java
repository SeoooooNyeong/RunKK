package com.a207.runkk.crew.chat;

import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.crew.chat.live.ChatRemovalReason;
import java.time.LocalDateTime;

/** 채팅 메시지를 지우거나(DELETED) 신고로 숨겼다(HIDDEN). 커밋 뒤 방 안 크루원에게 CrewChatRemoved를 보낸다(D-101, D-135). */
public record ChatMessageRemoved(
        long crewId, long messageId, ChatRemovalReason reason, LocalDateTime at)
        implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return at;
    }
}
