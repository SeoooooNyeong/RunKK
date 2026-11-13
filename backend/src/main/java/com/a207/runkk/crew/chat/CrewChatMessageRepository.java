package com.a207.runkk.crew.chat;

import java.util.List;
import java.util.Optional;

/** 채팅 메시지 저장 포트(설계 6.1.7a). 구현은 infra.chat.JpaCrewChatMessageRepository이고 crew.chat 밖에서 쓰지 않는다. */
public interface CrewChatMessageRepository {

    /** 새 메시지를 넣거나 바뀐 상태를 남긴다. 같은 (작성자, clientMessageId)가 있으면 무결성 위반(uq_crew_chat_client). */
    CrewChatMessage save(CrewChatMessage message);

    Optional<CrewChatMessage> findById(long id);

    /**
     * 행 잠금(FOR UPDATE)으로 최신 상태를 읽는다. 삭제·신고 숨김·숨김 해제가 겹쳐도 한쪽이 다른 쪽의 표시를 덮어쓰지 않게 차례로 세운다(버전 열이 없는 표라
     * 낙관적 잠금 대신).
     */
    Optional<CrewChatMessage> findByIdForUpdate(long id);

    Optional<CrewChatMessage> findByClientMessageId(long authorId, String clientMessageId);

    /**
     * 보이는(삭제·숨김이 아닌) 메시지를 커서에서 가까운 순으로 size + 1개(6.2 A-25, 색인 (crew_id, id DESC)): before·커서 없음은 ID
     * 내림차순, after는 ID 오름차순. 남는 한 개로 hasMore를 안다.
     */
    List<CrewChatMessage> findPage(long crewId, ChatCursor cursor);

    /**
     * 그 회원이 쓴 메시지의 작성자를 지운다(탈퇴, FR-021). 그 회원의 러닝 완료 시스템 메시지(RUN_FINISHED)는 먼저 삭제 표시해 목록에서 뺀다(피그마
     * 맞춤 W5 결정).
     */
    void anonymizeAuthor(long memberId);
}
