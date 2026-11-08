-- 런바디 러닝 점수(러닝 선택 G-30 "러닝 점수 78 ▲6", S15P31A207-242 후속). 결과 본문은 여전히 ai 서버에만 있고(D-442) 여기에는
-- ai 결과 axes의 점수 있는 축 평균(반올림) 하나만 둔다. 작업이 DONE이 될 때, 또는 결과를 처음 읽을 때 채운다.
-- V39·V40(크루)·V41(케이던스)는 다른 작업이 쓴다.
ALTER TABLE runbody_job ADD COLUMN overall_score INTEGER CHECK (overall_score BETWEEN 0 AND 100);

-- 회원별 최신순 목록(GET /api/v1/runbody/sessions). 회원 단독 색인을 대신한다(탈퇴 삭제도 이 색인의 앞 열을 쓴다).
DROP INDEX idx_runbody_job_member;
CREATE INDEX idx_runbody_job_member_created ON runbody_job(member_id, created_at DESC, record_id DESC);
