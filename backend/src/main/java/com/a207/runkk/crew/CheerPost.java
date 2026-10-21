package com.a207.runkk.crew;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.time.LocalDateTime;

/** 응원글 한 건(설계 6.1.7, FR-129~FR-131). */
@Entity
@DiscriminatorValue("CHEER")
public class CheerPost extends CrewPost {

    protected CheerPost() {}

    public CheerPost(long crewId, long authorId, String body, LocalDateTime now) {
        super(crewId, authorId, body, now);
    }

    /** 작성자, 본문, 작성일시. */
    @Override
    public PostSummary summary(long viewerId) {
        return baseSummary(viewerId, PostKind.CHEER, null);
    }
}
