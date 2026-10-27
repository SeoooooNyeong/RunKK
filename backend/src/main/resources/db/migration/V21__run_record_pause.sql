-- 자유런 일시정지 구간(부록 F F.5.3 V21, FR-036, FR-194, D-93, D-94).
-- 정지 구간은 "시작;끝" 줄 목록이고 기존 기록은 정지 없음('', 0)으로 채운다. 정지는 자유런에만 있다.
ALTER TABLE run_record ADD COLUMN pauses TEXT NOT NULL DEFAULT '';
ALTER TABLE run_record ADD COLUMN paused_millis BIGINT NOT NULL DEFAULT 0;
ALTER TABLE run_record ADD CONSTRAINT ck_run_record_pause
    CHECK (paused_millis >= 0 AND (mode = 'FREE' OR paused_millis = 0));

-- 티어 갱신 보정(TierReconcileService, D-108)의 validRunsAfter: 회원의 유효 기록을 저장 일시·기록 ID 순으로 센다
CREATE INDEX idx_run_record_member_valid_saved ON run_record (member_id, saved_at, record_id) WHERE valid;

-- 티어 반영 표시(총괄 결정, 설계 쟁점 7-3): 유효 기록마다 티어에 반영했는가. TierUpdateListener가 성공하면 true로 바꾸고
-- TierReconcileService가 false인 유효 기록을 저장 순서대로 반영한다. 이미 있는 기록은 반영한 것으로 본다(true로 채운 뒤 기본값 false).
ALTER TABLE run_record ADD COLUMN tier_applied BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE run_record ALTER COLUMN tier_applied SET DEFAULT FALSE;
CREATE INDEX idx_run_record_tier_pending ON run_record (saved_at) WHERE valid AND NOT tier_applied;
