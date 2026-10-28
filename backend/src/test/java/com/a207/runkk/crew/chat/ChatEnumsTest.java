package com.a207.runkk.crew.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.crew.chat.live.ChatRemovalReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 설계 v0.11 crew.chat enum(F.5.1, 06-04, 05-03 5.7.2). TC-6203. */
@DisplayName("v0.11 crew.chat enum")
class ChatEnumsTest {

    @Test
    @DisplayName("리액션은 LIKE·HEART, 채팅 삭제 사유는 DELETED·HIDDEN")
    void reactionsAndRemovalReasons() {
        assertThat(ReactionKind.values()).containsExactly(ReactionKind.LIKE, ReactionKind.HEART);
        assertThat(ChatRemovalReason.values())
                .containsExactly(ChatRemovalReason.DELETED, ChatRemovalReason.HIDDEN);
    }
}
