package com.a207.runkk.crew.chat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 채팅 조회 한 쪽(설계 6.1.7a, FR-209). 목록은 항상 오래된 → 새 순이다. 항목 구성은 F.2.2 ChatMessageItem과 같다.
 *
 * @param messages 메시지 줄
 * @param hasMore 이 쪽 밖에 더 있음(커서 방향)
 */
public record ChatPage(List<Line> messages, boolean hasMore) {

    public ChatPage {
        messages = List.copyOf(messages);
    }

    /**
     * 메시지 한 줄.
     *
     * @param messageId 메시지 ID
     * @param authorId 작성자 회원 ID, 탈퇴면 null
     * @param authorName 작성자 표시명(탈퇴면 "탈퇴한 사용자")
     * @param content 내용
     * @param sentAt 보낸 일시(서버 시간대 오프셋)
     * @param reactions 리액션 종류별 수(0인 종류는 없다)
     * @param myReactions 요청자가 단 리액션
     * @param mine 요청자 메시지
     * @param canDelete 요청자가 지울 수 있음(본인 또는 크루장·운영진)
     * @param clientMessageId 앱 메시지 ID(본인 메시지만, 아니면 null)
     */
    public record Line(
            long messageId,
            Long authorId,
            String authorName,
            String content,
            OffsetDateTime sentAt,
            Map<ReactionKind, Integer> reactions,
            Set<ReactionKind> myReactions,
            boolean mine,
            boolean canDelete,
            String clientMessageId) {

        public Line {
            reactions = Map.copyOf(reactions);
            myReactions = Set.copyOf(myReactions);
        }
    }
}
