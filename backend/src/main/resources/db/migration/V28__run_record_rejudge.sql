-- 일회성 재판정 표시(사용자 결정: 기존 무효 기록도 v0.10 새 규칙으로 다시 판정, FR-047). 배포 시점에 저장돼 있던 무효 기록에만 PENDING을 세운다.
-- 기동 뒤 RunRejudgeService(batch.running.RunRejudgeJob)가 기록마다 작은 트랜잭션으로 PENDING → VALID(유효로 바뀌어 부수 처리 대기) → DONE,
-- 또는 DONE(무효 그대로)·SKIPPED(경로 파기·미수집)로 바꾼다. 새로 받는 기록은 NULL이다. 상태가 바뀐 행은 다시 고르지 않으므로 몇 번 기동해도 결과가 같다.
ALTER TABLE run_record ADD COLUMN rejudge_state VARCHAR(8);
ALTER TABLE run_record ADD CONSTRAINT ck_run_record_rejudge_state
    CHECK (rejudge_state IS NULL OR rejudge_state IN ('PENDING', 'VALID', 'DONE', 'SKIPPED'));
UPDATE run_record SET rejudge_state = 'PENDING' WHERE judged AND NOT valid;
CREATE INDEX idx_run_record_rejudge ON run_record (rejudge_state, saved_at, record_id)
    WHERE rejudge_state IN ('PENDING', 'VALID');
