-- 기록을 시작한 기기(2026-11-01 사용자 결정 W23, D-378). 워치 단독 측정 기록은 WATCH, 폰 측정(워치 동행 포함)은 PHONE이다.
-- 기존 기록과 startDevice를 보내지 않는 옛 앱의 기록은 PHONE이다. 상수 기본값이라 PostgreSQL 11 이상에서는 행을 다시 쓰지 않는다.
ALTER TABLE run_record ADD COLUMN start_device VARCHAR(5) NOT NULL DEFAULT 'PHONE';
ALTER TABLE run_record ADD CONSTRAINT ck_run_record_start_device CHECK (start_device IN ('PHONE', 'WATCH'));
