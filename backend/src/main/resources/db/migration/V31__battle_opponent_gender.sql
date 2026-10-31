-- 대결 상대 캐릭터 성별(2026-10-31 결정). 앱이 상대 캐릭터 그림을 고른다. 매칭 때 한 번 정해 남겨 다시 연결·결과 화면에서도 같다.
-- 기존 대결은 NULL(앱은 기본 그림)이다.

-- 1. 기록 상대(고스트): 러너 기록이면 기록 주인 성별(없으면 무작위), 가상 기록이면 무작위. 셀럽·나와의 싸움은 NULL.
ALTER TABLE battle ADD COLUMN opponent_gender VARCHAR(6);
ALTER TABLE battle ADD CONSTRAINT ck_battle_opponent_gender
    CHECK (opponent_gender IS NULL OR opponent_gender IN ('MALE', 'FEMALE'));

-- 2. 랜덤·친구 대결: 다른 참가자에게 보이는 그 참가자의 성별(회원 성별, 없으면 무작위).
ALTER TABLE battle_participant ADD COLUMN shown_gender VARCHAR(6);
ALTER TABLE battle_participant ADD CONSTRAINT ck_battle_participant_shown_gender
    CHECK (shown_gender IS NULL OR shown_gender IN ('MALE', 'FEMALE'));
