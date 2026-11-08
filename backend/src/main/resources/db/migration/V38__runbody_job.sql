-- 런바디 ai 분석 작업(설계 05-19 D-441·D-443, FR-265). 결과는 ai 서버에만 있고 여기에는 상태만 둔다(D-442).
-- record_id는 run_record의 기록 ID 문자열이고 외래 키를 두지 않는다(다른 패키지 표와 같은 방식). 탈퇴·기록 삭제는 리스너가 지운다(D-448).
CREATE TABLE runbody_job (
    record_id VARCHAR(36) PRIMARY KEY,
    member_id BIGINT NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'ANALYZING', 'DONE', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    polls INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    submitted_at TIMESTAMP,
    next_poll_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_runbody_job_due ON runbody_job(status, next_poll_at);
CREATE INDEX idx_runbody_job_member ON runbody_job(member_id);
