package com.a207.runkk.crew.chat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 채팅 리액션 저장 포트(설계 6.1.7a, FR-210). 구현은 infra.chat.JpaChatReactionRepository다. */
public interface ChatReactionRepository {

    /** 리액션을 단다. 이미 있으면 그대로 두고 거짓(멱등, 동시 요청도 한 행). */
    boolean add(ChatReaction r);

    /** 리액션을 뗀다. 없으면 거짓. */
    boolean remove(long messageId, long memberId, ReactionKind kind);

    /** 메시지별 종류별 수. 리액션이 없는 메시지는 빠진다. */
    Map<Long, Map<ReactionKind, Integer>> countsOf(Collection<Long> messageIds);

    /** 그 회원이 단 메시지별 종류. */
    Map<Long, Set<ReactionKind>> mineOf(long memberId, Collection<Long> messageIds);

    /** 메시지 하나의 리액션 행(반응한 사람 목록, 피그마 F-09d). 반응 일시 오름차순, 같으면 회원 ID 오름차순. kind가 null이면 모든 종류. */
    List<ChatReaction> reactionsOf(long messageId, ReactionKind kind);

    /** 그 회원의 리액션을 모두 지운다(탈퇴, FR-021). */
    void deleteByMember(long memberId);
}
