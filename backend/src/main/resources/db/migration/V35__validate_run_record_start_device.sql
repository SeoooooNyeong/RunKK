-- V33의 시작 기기 CHECK 제약을 기존 행에 대해 검증한다(코드 리뷰 12차 BE12-2). Flyway는 마이그레이션마다 따로 트랜잭션을 커밋하므로
-- V33의 짧은 ACCESS EXCLUSIVE 잠금은 이미 풀렸고, VALIDATE CONSTRAINT는 SHARE UPDATE EXCLUSIVE 잠금이라 표를 훑는 동안에도 읽기·쓰기를 막지 않는다.
-- V33이 상수 기본값 'PHONE'으로 열을 더했으므로 기존 행은 모두 통과한다.
ALTER TABLE run_record VALIDATE CONSTRAINT ck_run_record_start_device;
