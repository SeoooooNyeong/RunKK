package com.a207.runkk.crew.chat;

/**
 * 채팅 보내기 입력(설계 6.1.7a, FR-208). 내용 규칙(공백 제거 뒤 1~500자)은 CrewChatMessage.write가 본다.
 *
 * @param clientMessageId 앱이 만든 메시지 ID(UUID 문자열 36자, 재전송 멱등)
 * @param content 내용
 */
public record ChatDraft(String clientMessageId, String content) {

    public ChatDraft {
        if (clientMessageId == null
                || clientMessageId.length() != CrewChatMessage.CLIENT_MESSAGE_ID_LENGTH) {
            throw new IllegalArgumentException("clientMessageId는 36자 UUID다");
        }
        if (content == null) {
            throw new IllegalArgumentException("내용이 비었다");
        }
        if (content.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("내용에 NUL 문자가 있다"); // PostgreSQL 문자열이 받지 않는다
        }
    }
}
