-- 시험 전용: develop에만 있는 V36(avatar 칼럼·selfie_job)을 흉내 낸다. V35_1과 겹치지 않는 표만 만든다(PlacementMigrationIntegrationTest).
CREATE TABLE sim_develop_v36 (id BIGINT PRIMARY KEY);
