package com.a207.runkk.crew;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.time.LocalDateTime;

/** 응원글 한 건(설계 6.1.7, FR-129~FR-131). 본문 1~200자 필수(D-99). */
@Entity
@DiscriminatorValue("CHEER")
public class CheerPost extends CrewPost {

    protected CheerPost() {}

    /** 빈 본문이면 IllegalArgumentException(400), 200자 초과 BODY_TOO_LONG. */
    public CheerPost(long crewId, long authorId, String body, LocalDateTime now) {
        super(crewId, authorId, requireText(body), now);
    }

    @Override
    protected int maxBody() {
        return SHORT_BODY;
    }

    @Override
    public PostKind kind() {
        return PostKind.CHEER;
    }

    /** 작성자, 본문, 작성일시. */
    @Override
    public PostSummary summary(long viewerId) {
        return baseSummary(viewerId);
    }
}
