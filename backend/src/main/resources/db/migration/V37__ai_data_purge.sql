CREATE TABLE ai_purge_request (
    member_id BIGINT PRIMARY KEY,
    status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'DONE', 'ALERTED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    requested_at TIMESTAMP NOT NULL,
    next_at TIMESTAMP NOT NULL,
    retention_check_done BOOLEAN NOT NULL DEFAULT FALSE,
    lease_token VARCHAR(36),
    lease_until TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_ai_purge_due ON ai_purge_request(status, next_at);

ALTER TABLE audit_record DROP CONSTRAINT ck_audit_record_action;
ALTER TABLE audit_record ADD CONSTRAINT ck_audit_record_action
    CHECK (action IN ('SIGN_UP_CONSENT', 'CONSENT_CHANGE', 'ROUTE_DISPOSAL', 'WITHDRAWAL',
                      'LEADER_TRANSFER', 'CREW_DELETE', 'KICK', 'POST_FORCE_DELETE',
                      'CELEBRITY_REGISTER', 'CELEBRITY_DELETE', 'SETTING_CHANGE',
                      'OPERATOR_LOGIN_SUCCESS', 'OPERATOR_LOGIN_FAILURE', 'RANKING_RECOMPUTED',
                      'MANAGER_APPOINT', 'MANAGER_DISMISS', 'CHAT_FORCE_DELETE', 'REPORT_HIDE',
                      'REPORT_DISMISS', 'AI_DATA_PURGE_OVERDUE'));
