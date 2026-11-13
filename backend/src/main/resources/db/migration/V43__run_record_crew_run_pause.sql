-- 크루런 일시정지(FR-300, S15P31A207-236, MR !91 BR-1): 크루런 기록도 정지 구간을 가진다. 대결만 정지가 없다.
-- V21의 ck_run_record_pause를 넓힌다. 새 제약은 옛 제약보다 느슨해 기존 행은 모두 맞는다.
ALTER TABLE run_record DROP CONSTRAINT ck_run_record_pause;
ALTER TABLE run_record ADD CONSTRAINT ck_run_record_pause
    CHECK (paused_millis >= 0 AND (mode IN ('FREE', 'CREW_RUN') OR paused_millis = 0));
