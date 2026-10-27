package com.a207.runkk.crew;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.time.LocalDateTime;

/**
 * 크루 공지 하나와 상단 고정 여부(설계 6.1.7, FR-205). 본문 1~1,000자. 권한(MANAGE_NOTICE)과 고정 수(MAX_PINNED, crew 행 잠금
 * 안)는 CrewPostService가 먼저 본다(D-113).
 */
@Entity
@DiscriminatorValue("NOTICE")
public class NoticePost extends CrewPost {

    /** 크루마다 보이는 고정 공지 상한(제안, D-113, D-154). */
    public static final int MAX_PINNED = 3;

    @Column(name = "pinned")
    private boolean pinned;

    protected NoticePost() {}

    public NoticePost(long crewId, long authorId, NoticeDraft draft, LocalDateTime now) {
        super(crewId, authorId, draft.body(), now);
        this.pinned = draft.pinned();
    }

    @Override
    protected int maxBody() {
        return LONG_BODY;
    }

    @Override
    public PostKind kind() {
        return PostKind.NOTICE;
    }

    public void pin() {
        pinned = true;
    }

    public void unpin() {
        pinned = false;
    }

    public boolean isPinned() {
        return pinned;
    }

    /** 본문 + 고정 여부. */
    @Override
    public PostSummary summary(long viewerId) {
        return baseSummary(viewerId).withNotice(new PostSummary.NoticeInfo(pinned));
    }
}
