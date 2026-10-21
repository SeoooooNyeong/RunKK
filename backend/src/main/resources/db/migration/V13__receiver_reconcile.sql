-- 커밋 뒤 수신자 일 단위 보정(코드 리뷰 2차 M4 결정). 리워드 적립·크루런 기록 연결 보정 배치가 저장 일시로 최근 기록을 고르고,
-- 리워드 하루 상한을 원래 사건일(저장 일시·판정 시각)로 센다.

-- 서버 저장 일시. 이전 기록은 종료 일시로 채운다(저장 일시를 남기지 않았다).
ALTER TABLE run_record ADD COLUMN saved_at TIMESTAMP(6);
UPDATE run_record SET saved_at = ended_at WHERE saved_at IS NULL;
ALTER TABLE run_record ALTER COLUMN saved_at SET NOT NULL;
CREATE INDEX idx_run_record_saved_at ON run_record (saved_at);

-- 회원이 이긴 판정(BATTLE_WIN 보정 대상)
CREATE INDEX idx_battle_member_win_judged_at ON battle (judged_at) WHERE winner_member_id IS NOT NULL;
