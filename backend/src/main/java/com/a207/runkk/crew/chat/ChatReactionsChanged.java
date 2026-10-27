package com.a207.runkk.crew.chat;

import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;

/** 메시지의 리액션이 바뀌었다. 커밋 뒤 방 안 크루원에게 CrewChatReactionChanged(수만)를 보낸다(FR-210). */
public record ChatReactionsChanged(long crewId, long messageId, LocalDateTime at)
        implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return at;
    }
}
