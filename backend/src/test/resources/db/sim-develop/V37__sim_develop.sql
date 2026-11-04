-- 시험 전용: develop에만 있는 V37(ai_purge_request·audit_record 제약)을 흉내 낸다(PlacementMigrationIntegrationTest).
CREATE TABLE sim_develop_v37 (id BIGINT PRIMARY KEY);
