-- 배치고사 진행(05-21 5절, D-475·D-476·D-514). 버전 35.1: prod는 V35 → 35.1 → (다음 릴리스) V36·V37 순서대로 받는다.
-- develop 환경 DB는 V36·V37이 이미 있어 develop 프로필만 spring.flyway.out-of-order: true로 35.1을 받는다(v0.12 릴리스 MR에서 지운다).
-- member·battle·battle_participant와 새 표 둘만 만지고 V36(avatar, selfie_job)·V37(ai_purge_request, audit_record)과 겹치지 않는다.

CREATE TABLE placement_progress (
    member_id BIGINT PRIMARY KEY REFERENCES member(id) ON DELETE CASCADE,
    counted_rounds SMALLINT NOT NULL DEFAULT 0,
    provisional_tier VARCHAR(10),
    confirmed_run_ordinal INTEGER,
    completed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_placement_progress_rounds CHECK (counted_rounds BETWEEN 0 AND 3),
    CONSTRAINT ck_placement_progress_tier CHECK (provisional_tier IS NULL
        OR provisional_tier IN ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'MASTER'))
);

CREATE TABLE placement_round (
    member_id BIGINT NOT NULL REFERENCES placement_progress(member_id) ON DELETE CASCADE,
    round_no SMALLINT NOT NULL,
    battle_id BIGINT NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    battle_distance VARCHAR(10) NOT NULL,
    run_distance_meters BIGINT NOT NULL,
    run_elapsed_millis BIGINT NOT NULL,
    average_pace_seconds INTEGER,
    verdict VARCHAR(5) NOT NULL,
    valid_run_ordinal INTEGER NOT NULL,
    counted_at TIMESTAMP NOT NULL,
    PRIMARY KEY (member_id, round_no),
    CONSTRAINT uq_placement_round_battle UNIQUE (battle_id),
    CONSTRAINT ck_placement_round_no CHECK (round_no BETWEEN 1 AND 3),
    CONSTRAINT ck_placement_round_verdict CHECK (verdict IN ('WIN', 'LOSE', 'DRAW'))
);

ALTER TABLE battle ADD COLUMN placement_round SMALLINT;
ALTER TABLE battle ADD CONSTRAINT ck_battle_placement_round
    CHECK (placement_round IS NULL OR (placement_round BETWEEN 1 AND 3 AND type = 'GHOST'));
ALTER TABLE battle_participant ADD COLUMN valid_run_ordinal INTEGER;
CREATE INDEX ix_battle_placement ON battle(judged_at) WHERE placement_round IS NOT NULL;
