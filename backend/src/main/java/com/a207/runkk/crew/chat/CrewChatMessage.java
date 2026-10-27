package com.a207.runkk.crew.chat;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.crew.CrewAction;
import com.a207.runkk.crew.CrewPermissionPolicy;
import com.a207.runkk.crew.CrewRelation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 크루 채팅 메시지 한 건의 내용과 표시 상태(설계 6.1.7a, FR-208, D-101). 삭제는 표시(deleted) 뒤 목록에서 빼고, 신고 숨김은 hidden이다.
 * 작성자가 탈퇴하면 authorId가 비고 내용은 남는다(FR-021). 위치 항목이 없다(NFR-021).
 */
@Entity
@Table(
        name = "crew_chat_message",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_crew_chat_client",
                        columnNames = {"author_id", "client_message_id"}))
public class CrewChatMessage {

    /** 삭제 판정이 목록 canDelete와 같은 권한표를 본다(D-188). 상태 없는 불변 표다. */
    private static final CrewPermissionPolicy POLICY = new CrewPermissionPolicy();

    /** 내용 글자 수 상한(코드 포인트, D-113, D-143). */
    public static final int MAX_CONTENT = 500;

    /**
     * 내용 칼럼의 UTF-16 단위 상한. PostgreSQL VARCHAR(500)은 글자(코드 포인트)로 세지만 시험용 H2는 UTF-16 단위로 세므로, 코드 포인트
     * 500자(최대 UTF-16 1,000 단위)가 들어가게 엔티티 매핑은 두 배로 둔다. 실제 표는 V23이 만든다.
     */
    private static final int CONTENT_COLUMN_UNITS = MAX_CONTENT * 2;

    /** 앱이 만든 메시지 ID 길이(UUID 문자열). */
    static final int CLIENT_MESSAGE_ID_LENGTH = 36;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "crew_id", nullable = false, updatable = false)
    private long crewId;

    @Column(name = "author_id")
    private Long authorId;

    @Column(
            name = "client_message_id",
            nullable = false,
            updatable = false,
            length = CLIENT_MESSAGE_ID_LENGTH)
    private String clientMessageId;

    @Column(name = "content", nullable = false, updatable = false, length = CONTENT_COLUMN_UNITS)
    private String content;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    protected CrewChatMessage() {}

    private CrewChatMessage(long crewId, long authorId, ChatDraft trimmed, LocalDateTime at) {
        this.crewId = crewId;
        this.authorId = authorId;
        this.clientMessageId = trimmed.clientMessageId();
        this.content = trimmed.content();
        this.sentAt = at;
    }

    /**
     * 작성. 앞뒤 공백을 지운 내용이 비면 CHAT_EMPTY, 코드 포인트 500자를 넘으면 BODY_TOO_LONG(FR-208, D-143).
     *
     * @param now 보낸 일시(서버 시계)
     */
    public static CrewChatMessage write(
            long crewId, long authorId, ChatDraft draft, LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("보낸 일시가 비었다");
        }
        String trimmed = draft.content().strip();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException(ReasonCode.CHAT_EMPTY);
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_CONTENT) {
            throw new BusinessRuleException(ReasonCode.BODY_TOO_LONG);
        }
        return new CrewChatMessage(
                crewId, authorId, new ChatDraft(draft.clientMessageId(), trimmed), now);
    }

    /**
     * 삭제 표시. 작성자 본인 또는 크루장·운영진만 지울 수 있고 그 밖은 PermissionDeniedException(FR-213).
     *
     * @param relation 요청자와 메시지 크루의 관계
     */
    public void delete(long requesterId, CrewRelation relation) {
        if (!mayDelete(isAuthor(requesterId), relation)) {
            throw new PermissionDeniedException("남의 채팅 메시지");
        }
        this.deleted = true;
    }

    /** 신고 숨김(CrewChatService.hideReported만 부른다, FR-215). */
    public void hide() {
        this.hidden = true;
    }

    /** 신고 숨김 해제(CrewChatService.unhideReported만 부른다, FR-216). */
    public void unhide() {
        this.hidden = false;
    }

    /** 목록에 보이는가(삭제·숨김이 아님, FR-209). */
    public boolean isVisible() {
        return !deleted && !hidden;
    }

    /** 작성자 탈퇴: 작성자를 지우고 내용은 남긴다(FR-021). */
    public void anonymizeAuthor() {
        this.authorId = null;
    }

    /** 요청자가 이 메시지를 쓴 사람인가(탈퇴로 작성자가 비었으면 아무도 아니다). */
    public boolean isAuthor(long memberId) {
        return authorId != null && authorId == memberId;
    }

    /** 본인 것이면 DELETE_OWN_CONTENT, 남의 것이면 DELETE_ANY_CONTENT를 권한표에 묻는다(F.5.1, D-98, D-188). */
    static boolean mayDelete(boolean own, CrewRelation relation) {
        return POLICY.isAllowed(
                relation, own ? CrewAction.DELETE_OWN_CONTENT : CrewAction.DELETE_ANY_CONTENT);
    }

    public Long id() {
        return id;
    }

    public long crewId() {
        return crewId;
    }

    /** 작성자 회원 ID, 탈퇴했으면 null. */
    public Long authorId() {
        return authorId;
    }

    public String clientMessageId() {
        return clientMessageId;
    }

    public String content() {
        return content;
    }

    public LocalDateTime sentAt() {
        return sentAt;
    }

    /** 보낸 일시를 그 시간대의 오프셋 시각으로(앱 계약 sentAt, F.2.2·F.3.2). */
    OffsetDateTime sentAtIn(ZoneId zone) {
        return sentAt.atZone(zone).toOffsetDateTime();
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isHidden() {
        return hidden;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CrewChatMessage m && id != null && Objects.equals(id, m.id);
    }

    @Override
    public int hashCode() {
        return CrewChatMessage.class.hashCode();
    }
}
