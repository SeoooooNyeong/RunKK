-- v0.10 유효 판정 변경(FR-047, 사용자 결정): 이웃 포인트 사이 시속이 25km/h를 넘는 구간은 기록 전체를 무효로 하지 않고 그 구간의 거리·시간만 뺀다.
-- distance_meters·elapsed_millis·average_pace_seconds는 뺀 뒤 남은 집계 값이고, 뺀 거리·시간을 따로 남긴다. 기존 기록은 뺀 것이 없다(0).
ALTER TABLE run_record ADD COLUMN excluded_meters BIGINT NOT NULL DEFAULT 0;
ALTER TABLE run_record ADD COLUMN excluded_millis BIGINT NOT NULL DEFAULT 0;
ALTER TABLE run_record ADD CONSTRAINT ck_run_record_excluded CHECK (excluded_meters >= 0 AND excluded_millis >= 0);
