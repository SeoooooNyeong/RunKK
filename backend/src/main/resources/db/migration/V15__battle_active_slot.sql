-- 활성 대결 1개 불변식과 버려진 대결 정리(활성 대결 1개 불변식 결정).
-- (1) CANCELLED: 출발하지 못한 대결(시작 보고 없이 10분 지난 기록 대결, 출발 시각이 10분 지나도 시작하지 못한 대결)의 끝 상태.
ALTER TABLE battle DROP CONSTRAINT ck_battle_status;
ALTER TABLE battle ADD CONSTRAINT ck_battle_status
    CHECK (status IN ('WAITING_START', 'IN_PROGRESS', 'COUNTDOWN', 'RESULT_PENDING', 'FINISHED', 'CANCELLED'));

-- (2) 회원별 활성 대결 칸. 활성(WAITING_START·IN_PROGRESS·COUNTDOWN)이면 회원 ID, 아니면 NULL(Battle.moveTo가 맞춘다).
-- 고유 인덱스는 NULL을 여럿 허용하므로 회원마다 활성 대결이 많아야 하나다(동시 생성 경합 포함).
ALTER TABLE battle_participant ADD COLUMN active_member_id BIGINT;

-- 이미 활성인 대결은 회원마다 가장 최근 것 하나에만 칸을 채운다(그 밖의 활성 대결은 정리 배치·시계가 끝낸다).
UPDATE battle_participant p
SET active_member_id = p.member_id
WHERE p.id IN (
    SELECT DISTINCT ON (bp.member_id) bp.id
    FROM battle_participant bp
    JOIN battle b ON b.id = bp.battle_id
    WHERE b.status IN ('WAITING_START', 'IN_PROGRESS', 'COUNTDOWN')
    ORDER BY bp.member_id, b.id DESC
);

CREATE UNIQUE INDEX uq_battle_participant_active_member ON battle_participant (active_member_id);

-- 버려진 대결 찾기(BattleRepository.findAbandoned)
CREATE INDEX idx_battle_status_created ON battle (status, created_at);
