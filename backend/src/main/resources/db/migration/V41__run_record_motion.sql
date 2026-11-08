-- 기기가 잰 속도·케이던스 표본(2026-11-08 사용자 결정 "다 보내, 없으면 계산하고", S15P31A207-242).
-- 위치·심박처럼 압축 바이너리 열(running.MotionSamples)이다. 기존 기록과 이 값을 보내지 않는 옛 앱의 기록은 NULL이다.
-- 기본값이 없는 NULL 열이라 행을 다시 쓰지 않는다. 경로를 파기하면(위치 동의 철회) 함께 지운다.
ALTER TABLE run_record ADD COLUMN motion_data BYTEA;
