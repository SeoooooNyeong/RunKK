package com.a207.runkk.crew.chat.live;

import com.a207.runkk.crew.chat.CrewChatSignal;
import java.time.OffsetDateTime;

/**
 * 새 채팅 메시지(서버 → 앱, 5.7.2, F.3.2). 방 안의 현재 크루원에게 커밋 뒤 보낸다. 위치 항목이 없다(NFR-021).
 *
 * @param crewId 크루 ID
 * @param messageId 메시지 ID
 * @param authorId 작성자 회원 ID, 탈퇴면 null
 * @param authorName 작성자 표시명
 * @param content 내용
 * @param sentAt 보낸 일시(+09:00 오프셋)
 * @param clientMessageId 작성자 앱이 자기 낙관적 표시와 맞추는 ID
 */
public record CrewChatPosted(
        long crewId,
        long messageId,
        Long authorId,
        String authorName,
        String content,
        OffsetDateTime sentAt,
        String clientMessageId)
        implements CrewChatSignal {

    @Override
    public String type() {
        return "CrewChatPosted";
    }
}
