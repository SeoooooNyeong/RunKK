ALTER TABLE member_consent
    ADD COLUMN notice_version INTEGER NOT NULL DEFAULT 1 CHECK (notice_version > 0);

CREATE TABLE coach_daily_usage (
    member_id BIGINT PRIMARY KEY REFERENCES member(id) ON DELETE CASCADE,
    usage_day DATE NOT NULL,
    answered_count INTEGER NOT NULL CHECK (answered_count >= 0),
    lease_token VARCHAR(36),
    lease_until TIMESTAMP,
    CHECK ((lease_token IS NULL) = (lease_until IS NULL))
);

CREATE TABLE coach_conversation (
    conversation_id VARCHAR(64) PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    first_question VARCHAR(80) NOT NULL,
    last_asked_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_coach_conversation_member_asked ON coach_conversation (member_id, last_asked_at DESC);
CREATE INDEX ix_coach_conversation_expiry ON coach_conversation (last_asked_at);
