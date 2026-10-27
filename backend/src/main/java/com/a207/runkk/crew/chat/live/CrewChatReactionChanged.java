package com.a207.runkk.crew.chat.live;

import com.a207.runkk.crew.chat.CrewChatSignal;
import com.a207.runkk.crew.chat.ReactionKind;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 메시지 리액션 수가 바뀌었다(서버 → 앱, 5.7.2, F.3.2). 누가 달았는지는 싣지 않는다(본인 상태는 REST 응답으로 안다).
 *
 * @param crewId 크루 ID
 * @param messageId 메시지 ID
 * @param reactions 종류별 수(0인 종류는 없다, 종류 선언 순서)
 */
public record CrewChatReactionChanged(
        long crewId, long messageId, Map<ReactionKind, Integer> reactions)
        implements CrewChatSignal {

    public CrewChatReactionChanged {
        Map<ReactionKind, Integer> ordered = new EnumMap<>(ReactionKind.class);
        ordered.putAll(reactions);
        reactions = Collections.unmodifiableMap(ordered);
    }

    @Override
    public String type() {
        return "CrewChatReactionChanged";
    }
}
