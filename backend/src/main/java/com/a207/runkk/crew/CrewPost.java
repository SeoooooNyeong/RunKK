package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 크루 게시글의 공통 상태(설계 6.1.7, D-99). 하위 클래스는 CheerPost, SchedulePost(추상) ← CrewRunPost·MeetupPost,
 * NoticePost, PollPost, BoardPost이고 JPA 단일 테이블 상속(구분 열 post_kind)이다. 탈퇴한 작성자는 authorId가 null이고 "탈퇴한
 * 사용자"로 보인다(FR-021). 신고 숨김(hidden)은 CrewPostService.hideReported·unhideReported만 바꾼다(D-104, D-135).
 */
@Entity
@Table(
        name = "crew_post",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_crew_post_series_scheduled",
                        columnNames = {"series_id", "scheduled_at"}))
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "post_kind", length = 10)
public abstract class CrewPost {

    /** 삭제 판정과 목록 canDelete가 같은 권한표를 본다(D-188). 상태 없는 불변 표다. */
    private static final CrewPermissionPolicy POLICY = new CrewPermissionPolicy();

    /** 공지·게시글 본문 상한(D-99, 코드 포인트). */
    static final int LONG_BODY = 1000;

    /** 응원·크루런·대면 모임·투표 본문 상한(D-99). */
    static final int SHORT_BODY = 200;

    /**
     * 글자(코드 포인트) 하나가 차지할 수 있는 UTF-16 단위 수. PostgreSQL VARCHAR(n)은 글자로 세지만 시험용 H2는 UTF-16 단위로 세므로,
     * 엔티티 칼럼 매핑만 상한의 두 배로 둔다(실제 표는 V22 그대로, 채팅 CrewChatMessage와 같다).
     */
    static final int UTF16_UNITS_PER_CHAR = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "crew_id", nullable = false)
    private long crewId;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "body", nullable = false, length = LONG_BODY * UTF16_UNITS_PER_CHAR)
    private String body;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    /** 낙관적 잠금(크루런·대면 모임의 참석 응답 경합, 5.3). 단일 테이블 상속이라 뿌리에 둔다. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CrewPost() {}

    /** 본문이 하위 클래스의 maxBody()를 넘으면 BODY_TOO_LONG. 비운 본문은 빈 문자열로 둔다. */
    protected CrewPost(long crewId, long authorId, String body, LocalDateTime now) {
        String text = body == null ? "" : body;
        if (codePoints(text) > maxBody()) {
            throw new BusinessRuleException(ReasonCode.BODY_TOO_LONG);
        }
        this.crewId = crewId;
        this.authorId = authorId;
        this.body = text;
        this.createdAt = now;
    }

    /** 본문 고치기(크루런 모집글 수정, W6). 상한은 생성자와 같은 maxBody()이고 넘으면 BODY_TOO_LONG, null은 빈 문자열이다. */
    protected void changeBody(String body) {
        String text = body == null ? "" : body;
        if (codePoints(text) > maxBody()) {
            throw new BusinessRuleException(ReasonCode.BODY_TOO_LONG);
        }
        this.body = text;
    }

    /** 글자 수(코드 포인트). 이모지 같은 서로게이트 쌍을 한 글자로 센다(앱과 같은 기준, D-143). 본문·투표 질문·선택지·장소명 상한에 쓴다. */
    static int codePoints(String text) {
        return text.codePointCount(0, text.length());
    }

    /** 종류별 본문 상한(코드 포인트): 응원·크루런·대면 모임·투표 200, 공지·게시글 1,000(D-99, D-143). */
    protected abstract int maxBody();

    /** 게시 종류(구분 값과 같다). */
    public abstract PostKind kind();

    /** 본문이 필수인 종류(응원·공지)의 빈 본문 검사. 비었으면 IllegalArgumentException(400). */
    protected static String requireText(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("본문이 비었다");
        }
        return body;
    }

    /**
     * 작성자면 SELF, 크루장·운영진(L·M)이면 FORCED(감사 대상), 그 밖(크루 밖 포함)은 PermissionDeniedException(srs.md 6.3
     * 5.4 삭제 표, F.5.1 DELETE_OWN_CONTENT·DELETE_ANY_CONTENT, FR-213). 관계 비교는
     * 권한표(CrewPermissionPolicy)에 묻는다(D-188).
     */
    public DeletionKind delete(long requesterId, CrewRelation relation) {
        boolean own = Objects.equals(authorId, requesterId);
        POLICY.check(relation, own ? CrewAction.DELETE_OWN_CONTENT : CrewAction.DELETE_ANY_CONTENT);
        deleted = true;
        return own ? DeletionKind.SELF : DeletionKind.FORCED;
    }

    /** 신고 숨김(FR-215). CrewPostService.hideReported만 부른다. */
    public void hide() {
        hidden = true;
    }

    /** 신고 숨김 해제(FR-216). CrewPostService.unhideReported만 부른다. */
    public void unhide() {
        hidden = false;
    }

    /** 목록 표시: !deleted && !hidden(FR-212). */
    public boolean isVisible() {
        return !deleted && !hidden;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isHidden() {
        return hidden;
    }

    /** 목록 한 줄. 하위 클래스가 구현한다. */
    public abstract PostSummary summary(long viewerId);

    /** 작성자 탈퇴: authorId = null. */
    public void anonymizeAuthor() {
        authorId = null;
    }

    /** 종류 정보가 없는 공통 줄. 하위 클래스가 with…로 덧붙인다. */
    protected PostSummary baseSummary(long viewerId) {
        return PostSummary.of(
                new PostHeader(id == null ? 0 : id, kind(), authorId, body, createdAt),
                Objects.equals(authorId, viewerId));
    }

    public Long getId() {
        return id;
    }

    public long crewId() {
        return crewId;
    }

    public Long authorId() {
        return authorId;
    }

    public String body() {
        return body;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
