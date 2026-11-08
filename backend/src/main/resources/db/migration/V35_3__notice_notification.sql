-- 운영자 공지 알림(핫픽스 v0.11.4, S15P31A207-275). 버전 35.3: prod는 V35_2 → 35.3 → (다음 릴리스) V36·V37 순서로 받는다.
-- develop DB는 V36·V37이 이미 있어 develop 프로필의 out-of-order로 35.3을 받는다. V36(avatar, selfie_job)·V37(ai_purge_request,
-- audit_record 제약)은 notification 표를 만지지 않는다. 공지 발송 감사는 기존 SETTING_CHANGE를 쓴다(V37이 audit_record 행위 제약을
-- 다시 만들므로 새 행위 종류를 더하지 않는다).

-- 알림 종류 + NOTICE(운영자 공지)
ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type
    CHECK (type IN ('PLAN_MISSED', 'BODY_SHAPE_CHANGED', 'JOIN_REQUESTED', 'JOIN_DECIDED',
                    'JOIN_EXPIRED', 'LEADER_TRANSFERRED', 'CREW_DELETED', 'KICKED',
                    'CREW_RUN_RECRUIT', 'CREW_RUN_SOON', 'OPPONENT_ARRIVED', 'BATTLE_CORRECTED',
                    'CREW_NOTICE', 'CREW_POLL', 'POLL_CLOSING_SOON', 'CREW_SCHEDULE', 'CREW_CHAT',
                    'CREW_GOAL_ACHIEVED', 'MANAGER_CHANGED', 'BACKGROUND_UNLOCKED', 'REPORT_RESOLVED',
                    'NOTICE'));

-- 공지 제목(다른 종류는 NULL, 앱이 종류로 제목을 정한다)
ALTER TABLE notification ADD COLUMN title VARCHAR(60);

-- 공지 링크(구글 폼 https 주소)를 대상 ID에 둔다. 길이만 늘린다(PostgreSQL은 표를 다시 쓰지 않는다).
ALTER TABLE notification ALTER COLUMN target_id TYPE VARCHAR(300);
