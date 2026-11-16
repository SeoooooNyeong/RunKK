package com.a207.runkk.crew.chat.live;

import com.a207.runkk.crew.chat.ChatMessageKind;
import com.a207.runkk.crew.chat.CrewChatSignal;
import java.time.OffsetDateTime;

/**
 * 새 채팅 메시지(서버 → 앱, 5.7.2, F.3.2). 방 안의 현재 크루원에게 커밋 뒤 보낸다. 위치 항목이 없다(NFR-021). 피그마 맞춤 W5(C7)에서 메시지
 * 종류·작성자 직책·러닝 거리를 더했다(러닝 완료 시스템 메시지는 닉네임·거리만 싣는다).
 *
 * @param crewId 크루 ID
 * @param messageId 메시지 ID
 * @param authorId 작성자 회원 ID, 탈퇴면 null
 * @param authorName 작성자 표시명
 * @param content 내용
 * @param sentAt 보낸 일시(+09:00 오프셋)
 * @param clientMessageId 작성자 앱이 자기 낙관적 표시와 맞추는 ID
 * @param messageKind 메시지 종류(TEXT, RUN_FINISHED)
 * @param authorRole 작성자의 지금 크루 역할 이름(crew.CrewRole 상수 LEADER·MANAGER·MEMBER, live는 crew를 import하지
 *     않아 문자열로 싣는다), 탈퇴했거나 크루를 떠났으면 null
 * @param runDistanceMeters 러닝 완료 메시지의 거리(미터), 보통 메시지면 null
 * @param authorFaceKey 작성자 아바타 얼굴 키, 얼굴을 안 골랐거나 탈퇴 작성자면 null(설계 5.31 [임시:D-성-229], 폴링
 *     ChatMessageItem과 같은 항목)
 * @param authorShownGender 작성자 표시 성별 이름(MALE | FEMALE), 탈퇴 작성자면 null
 */
public record CrewChatPosted(
        long crewId,
        long messageId,
        Long authorId,
        String authorName,
        String content,
        OffsetDateTime sentAt,
        String clientMessageId,
        ChatMessageKind messageKind,
        String authorRole,
        Long runDistanceMeters,
        String authorFaceKey,
        String authorShownGender)
        implements CrewChatSignal {

    @Override
    public String type() {
        return "CrewChatPosted";
    }
}
