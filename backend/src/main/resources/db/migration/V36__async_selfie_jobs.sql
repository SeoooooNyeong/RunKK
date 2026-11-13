ALTER TABLE avatar ADD COLUMN head_job_id VARCHAR(64);
ALTER TABLE avatar ADD COLUMN pending_face_request VARCHAR(64);

CREATE TABLE selfie_job (
    request_id VARCHAR(64) PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    job_id VARCHAR(64),
    status VARCHAR(16),
    head_id VARCHAR(64),
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    attempted_at TIMESTAMP NOT NULL,
    finished BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (member_id, job_id)
);
CREATE INDEX ix_selfie_job_pending ON selfie_job(member_id, finished);
