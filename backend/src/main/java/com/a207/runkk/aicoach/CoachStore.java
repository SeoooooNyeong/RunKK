package com.a207.runkk.aicoach;

import java.time.LocalDateTime;
import java.util.List;

public interface CoachStore {
    record Conversation(String conversationId, String firstQuestion, LocalDateTime lastAskedAt) {}

    record Lease(long memberId, String token) {}

    Lease claim(long memberId, int limit, LocalDateTime now);

    boolean hasConversation(long memberId, String id, LocalDateTime cutoff);

    List<Conversation> conversations(long memberId, LocalDateTime cutoff);

    boolean started(Lease lease, String id, String preview, boolean continuing, LocalDateTime at);

    boolean answered(Lease lease, LocalDateTime at);

    void release(Lease lease);

    int purge(LocalDateTime cutoff);

    void deleteMember(long memberId);
}
