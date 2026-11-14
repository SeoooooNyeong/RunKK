package com.a207.runkk.crew.chat;

import com.a207.runkk.crew.CrewRole;
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
     * @param kind 메시지 종류(보통 TEXT, 러닝 완료 RUN_FINISHED, W5)
     * @param authorRole 작성자의 지금 크루 역할, 탈퇴했거나 크루를 떠났으면 null(크루장 배지, W5)
     * @param runDistanceMeters 러닝 완료 메시지의 거리(미터), 보통 메시지면 null(W5)
     * @param authorFaceKey 작성자 아바타 얼굴 키, 얼굴을 안 골랐거나 탈퇴 작성자면 null(설계 5.31 [임시:D-성-229])
     * @param authorShownGender 작성자 표시 성별 이름(MALE | FEMALE), 탈퇴 작성자면 null
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
            String clientMessageId,
            ChatMessageKind kind,
            CrewRole authorRole,
            Long runDistanceMeters,
            String authorFaceKey,
            String authorShownGender) {

        public Line {
            reactions = Map.copyOf(reactions);
            myReactions = Set.copyOf(myReactions);
        }

        /** 작성자 역할 상수 이름(web.chat은 crew를 import하지 않아 이름으로 싣는다), 없으면 null. */
        public String authorRoleName() {
            return authorRole == null ? null : authorRole.name();
        }
    }
}
