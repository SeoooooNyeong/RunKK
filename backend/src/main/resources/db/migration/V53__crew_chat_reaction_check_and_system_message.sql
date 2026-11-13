-- 피그마 맞춤 W5(S15P31A207-296, 사용자 결정 C7): 채팅 반응 CHECK 추가와 러닝 완료 시스템 메시지.
-- 계약: docs/sw/maintenance/2026-11-09_피그마맞춤-성/contract-W5.md

-- 반응 종류에 CHECK를 더한다(V23 ck_crew_chat_reaction_kind는 LIKE·HEART만 받았다). kind VARCHAR(10)은 그대로 둔다
ALTER TABLE crew_chat_reaction DROP CONSTRAINT ck_crew_chat_reaction_kind;
ALTER TABLE crew_chat_reaction
    ADD CONSTRAINT ck_crew_chat_reaction_kind CHECK (kind IN ('LIKE', 'HEART', 'CHECK'));

-- 메시지 종류. 기존 행은 모두 크루원이 보낸 글(TEXT)이다. RUN_FINISHED는 크루원이 유효 러닝을 마쳤을 때 서버가 남기고
-- 작성자(author_id)는 러닝한 크루원, 거리(run_distance_meters)만 싣는다(시간·위치 없음, NFR-021).
-- 같은 기록의 메시지 두 개는 기록 ID로 정한 client_message_id와 uq_crew_chat_client가 막는다
ALTER TABLE crew_chat_message ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'TEXT';
ALTER TABLE crew_chat_message ADD COLUMN run_distance_meters BIGINT;
ALTER TABLE crew_chat_message
    ADD CONSTRAINT ck_crew_chat_message_kind CHECK (kind IN ('TEXT', 'RUN_FINISHED'));
ALTER TABLE crew_chat_message
    ADD CONSTRAINT ck_crew_chat_message_run_distance CHECK (
        (kind = 'TEXT' AND run_distance_meters IS NULL)
        OR (kind = 'RUN_FINISHED' AND run_distance_meters IS NOT NULL AND run_distance_meters >= 0)
    );
