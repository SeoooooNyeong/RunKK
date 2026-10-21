package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 크루 게시글의 공통 상태(설계 6.1.7, 6.3 상속 깊이 1). 하위 클래스는 CheerPost, CrewRunPost이고 JPA 단일 테이블 상속(구분 열
 * post_kind)이다(13.1). 탈퇴한 작성자는 authorId가 null이고 "탈퇴한 사용자"로 보인다(FR-021).
 */
@Entity
@Table(name = "crew_post")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "post_kind", length = 10)
public abstract class CrewPost {

    /** 본문 상한(FR-129). */
    static final int MAX_BODY = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "crew_id", nullable = false)
    private long crewId;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "body", nullable = false, length = MAX_BODY)
    private String body;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    /** 낙관적 잠금(크루런 대기실·투표, 5.3). 단일 테이블 상속이라 뿌리에 둔다. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CrewPost() {}

    /** 200자 초과 BODY_TOO_LONG. */
    protected CrewPost(long crewId, long authorId, String body, LocalDateTime now) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("본문이 비었다");
        }
        if (body.length() > MAX_BODY) {
            throw new BusinessRuleException(ReasonCode.BODY_TOO_LONG);
        }
        this.crewId = crewId;
        this.authorId = authorId;
        this.body = body;
        this.createdAt = now;
    }

    /**
     * 작성자면 SELF, 크루장이면 FORCED(감사 대상), 그 밖(크루 밖 포함)은 PermissionDeniedException(srs.md 6.3 5.4 삭제 표).
     */
    public DeletionKind delete(long requesterId, CrewRelation relation) {
        if (relation == CrewRelation.OUTSIDER) {
            throw new PermissionDeniedException("권한 없음");
        }
        DeletionKind kind;
        if (Objects.equals(authorId, requesterId)) {
            kind = DeletionKind.SELF;
        } else if (relation == CrewRelation.LEADER) {
            kind = DeletionKind.FORCED;
        } else {
            throw new PermissionDeniedException("권한 없음");
        }
        deleted = true;
        return kind;
    }

    /** 목록 표시: !deleted. */
    public boolean isVisible() {
        return !deleted;
    }

    /** 목록 한 줄. 하위 클래스가 구현한다. */
    public abstract PostSummary summary(long viewerId);

    /** 작성자 탈퇴: authorId = null. */
    public void anonymizeAuthor() {
        authorId = null;
    }

    protected PostSummary baseSummary(long viewerId, PostKind kind, PostSummary.CrewRunInfo run) {
        return new PostSummary(
                id == null ? 0 : id,
                kind,
                authorId,
                body,
                createdAt,
                Objects.equals(authorId, viewerId),
                run);
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
}
