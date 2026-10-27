package com.a207.runkk.crew.chat;

import java.util.Map;
import java.util.Set;

/**
 * 리액션 결과(설계 6.1.7a, FR-210).
 *
 * @param messageId 메시지 ID
 * @param reactions 리액션 종류별 수(0인 종류는 없다)
 * @param myReactions 요청자가 단 리액션
 */
public record ReactionView(
        long messageId, Map<ReactionKind, Integer> reactions, Set<ReactionKind> myReactions) {

    public ReactionView {
        reactions = Map.copyOf(reactions);
        myReactions = Set.copyOf(myReactions);
    }
}
