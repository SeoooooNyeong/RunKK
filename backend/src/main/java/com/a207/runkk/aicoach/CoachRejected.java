package com.a207.runkk.aicoach;

public final class CoachRejected extends RuntimeException {
    public enum Reason {
        FEATURE_CLOSED,
        CONSENT_REQUIRED,
        CONVERSATION_EXPIRED,
        COACH_QUOTA_EXCEEDED,
        QUESTION_TOO_LONG,
        RETRY
    }

    private final Reason reason;

    public CoachRejected(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
