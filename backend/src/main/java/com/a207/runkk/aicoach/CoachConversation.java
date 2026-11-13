package com.a207.runkk.aicoach;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "coach_conversation")
public class CoachConversation {
    @Id
    @Column(length = 64)
    private String conversationId;

    @Column(nullable = false)
    private long memberId;

    @Column(nullable = false, length = 80)
    private String firstQuestion;

    @Column(nullable = false)
    private LocalDateTime lastAskedAt;

    protected CoachConversation() {}

    public CoachConversation(String id, long memberId, String preview, LocalDateTime at) {
        this.conversationId = id;
        this.memberId = memberId;
        this.firstQuestion = preview;
        this.lastAskedAt = at;
    }

    public boolean ownedBy(long memberId) {
        return this.memberId == memberId;
    }

    public void touch(LocalDateTime at) {
        lastAskedAt = at;
    }

    public CoachStore.Conversation view() {
        return new CoachStore.Conversation(conversationId, firstQuestion, lastAskedAt);
    }
}
