package com.a207.runkk.crew.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.crew.CrewRelation;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 설계 6.1.7a CrewChatMessage·ChatDraft·ChatCursor. TC-6112~TC-6115. */
@DisplayName("crew.chat CrewChatMessage·ChatDraft·ChatCursor")
class CrewChatMessageTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 9, 0);
    private static final String UUID_1 = "00000000-0000-0000-0000-000000000001";

    private static CrewChatMessage write(String content) {
        return CrewChatMessage.write(7, 3, new ChatDraft(UUID_1, content), NOW);
    }

    private static ReasonCode reasonOf(Runnable r) {
        try {
            r.run();
        } catch (BusinessRuleException e) {
            return e.reason();
        }
        return null;
    }

    @Test
    @DisplayName("TC-6113 내용 경계: 빈 값·공백만 CHAT_EMPTY, 1자·500자 생성, 501자 BODY_TOO_LONG, 앞뒤 공백은 지우고 센다")
    void contentBoundaries() {
        assertThat(reasonOf(() -> write(""))).isEqualTo(ReasonCode.CHAT_EMPTY);
        assertThat(reasonOf(() -> write("   "))).isEqualTo(ReasonCode.CHAT_EMPTY);
        assertThat(write("a").content()).isEqualTo("a");
        assertThat(write("가".repeat(500)).content()).hasSize(500);
        assertThat(reasonOf(() -> write("a".repeat(501)))).isEqualTo(ReasonCode.BODY_TOO_LONG);
        CrewChatMessage padded = write("  " + "a".repeat(500) + "  ");
        assertThat(padded.content()).isEqualTo("a".repeat(500));
        assertThat(padded.crewId()).isEqualTo(7);
        assertThat(padded.authorId()).isEqualTo(3L);
        assertThat(padded.clientMessageId()).isEqualTo(UUID_1);
        assertThat(padded.sentAt()).isEqualTo(NOW);
        assertThat(padded.isVisible()).isTrue();
    }

    @Test
    @DisplayName("TC-6114 글자 수는 코드 포인트: 이모지 500개 생성(UTF-16 1,000), 501개 BODY_TOO_LONG")
    void codePoints() {
        String runner = "🏃";
        assertThat(write(runner.repeat(500)).content()).hasSize(1_000);
        assertThat(reasonOf(() -> write(runner.repeat(501)))).isEqualTo(ReasonCode.BODY_TOO_LONG);
    }

    @Test
    @DisplayName("TC-6115 삭제: 작성자·크루장·운영진 허용, 다른 크루원·크루 밖 거부. 숨김·해제, 작성자 익명화")
    void deleteHideAnonymize() {
        CrewChatMessage own = write("안녕");
        own.delete(3, CrewRelation.MEMBER);
        assertThat(own.isDeleted()).isTrue();
        assertThat(own.isVisible()).isFalse();

        CrewChatMessage byLeader = write("안녕");
        byLeader.delete(1, CrewRelation.LEADER);
        assertThat(byLeader.isDeleted()).isTrue();

        CrewChatMessage byManager = write("안녕");
        byManager.delete(2, CrewRelation.MANAGER);
        assertThat(byManager.isDeleted()).isTrue();

        CrewChatMessage other = write("안녕");
        assertThatThrownBy(() -> other.delete(4, CrewRelation.MEMBER))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> other.delete(9, CrewRelation.OUTSIDER))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(other.isDeleted()).isFalse();

        other.hide();
        assertThat(other.isHidden()).isTrue();
        assertThat(other.isVisible()).isFalse();
        other.unhide();
        assertThat(other.isVisible()).isTrue();

        other.anonymizeAuthor();
        assertThat(other.authorId()).isNull();
        assertThat(other.content()).isEqualTo("안녕");
    }

    @Test
    @DisplayName("ChatDraft: clientMessageId는 36자, 내용은 null이 아니다")
    void draft() {
        assertThatThrownBy(() -> new ChatDraft("short", "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatDraft(null, "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatDraft(UUID_1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatDraft(UUID_1, "a\u0000b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName(
            "TC-6112 ChatCursor: before·after 함께면 400, size 없음·0은 50, 100 초과는 100(거부하지 않는다, D-165)")
    void cursor() {
        assertThatThrownBy(() -> ChatCursor.of(10L, 5L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ChatCursor.of(null, null, null).size()).isEqualTo(50);
        assertThat(ChatCursor.of(null, null, 0).size()).isEqualTo(50);
        assertThat(ChatCursor.of(null, null, 100).size()).isEqualTo(100);
        assertThat(ChatCursor.of(null, null, 101).size()).isEqualTo(100);
        assertThat(ChatCursor.of(100L, null, 20)).isEqualTo(new ChatCursor(100L, null, 20));
    }
}
