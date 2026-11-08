-- 크루런 합산 방식(S15P31A207-236): 순위 경주의 도착 일시·순위를 지우고, 그만둠을 "끝냄"으로 바꾸며, 끝낼 때의 거리·경과를 남긴다.
-- 크루 탭은 1차 배포에서 잠겨 있어 지우는 도착·순위 값은 쓰이지 않았다.
ALTER TABLE crew_run_participation DROP COLUMN arrived_at;
ALTER TABLE crew_run_participation DROP COLUMN finish_rank;
ALTER TABLE crew_run_participation RENAME COLUMN quit TO ended;
ALTER TABLE crew_run_participation ADD COLUMN final_distance_m BIGINT;
ALTER TABLE crew_run_participation ADD COLUMN final_elapsed_ms BIGINT;
