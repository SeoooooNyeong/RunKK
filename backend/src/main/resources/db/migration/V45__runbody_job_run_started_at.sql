-- 런바디 러닝 선택 목록(G-30)과 점수 변화를 러닝 시작 순서로(MR !91 e2e 3). 작업에 러닝 시작 일시를 두고, 늦게 올린 예전 러닝이
-- 맨 위에 오거나 엉뚱한 러닝과 비교되지 않게 한다. 기존 행은 run_record에서 채우고 기록이 없으면 작업을 만든 일시로 둔다.
ALTER TABLE runbody_job ADD COLUMN run_started_at TIMESTAMP;
UPDATE runbody_job j SET run_started_at = r.started_at FROM run_record r WHERE r.record_id = j.record_id;
UPDATE runbody_job SET run_started_at = created_at WHERE run_started_at IS NULL;
ALTER TABLE runbody_job ALTER COLUMN run_started_at SET NOT NULL;

-- 회원별 최신순 목록 색인을 시작 일시 기준으로 바꾼다(V42의 created_at 색인을 대신한다).
DROP INDEX idx_runbody_job_member_created;
CREATE INDEX idx_runbody_job_member_started ON runbody_job(member_id, run_started_at DESC, record_id DESC);
