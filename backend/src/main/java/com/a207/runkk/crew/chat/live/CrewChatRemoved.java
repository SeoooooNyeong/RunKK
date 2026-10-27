package com.a207.runkk.crew.chat.live;

import com.a207.runkk.crew.chat.CrewChatSignal;

/**
 * 채팅 메시지가 삭제(DELETED)되거나 신고로 숨겨졌다(HIDDEN)(서버 → 앱, 5.7.2, F.3.2). 앱은 "삭제된 메시지" 자리표시로 바꾼다(D-160).
 *
 * @param crewId 크루 ID
 * @param messageId 메시지 ID
 * @param reason 사유
 */
public record CrewChatRemoved(long crewId, long messageId, ChatRemovalReason reason)
        implements CrewChatSignal {

    @Override
    public String type() {
        return "CrewChatRemoved";
    }
}
