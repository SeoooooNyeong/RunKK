-- v0.11 S0 계약 고정(부록 F F.5.3 V19): enum 값 목록 CHECK만 넓힌다. 뒤 단계(S1~S4)가 쓸 값까지 한 번에 넣고 데이터는 바꾸지 않는다.

-- 알림 종류 + 9종(CREW_NOTICE, CREW_POLL, POLL_CLOSING_SOON, CREW_SCHEDULE, CREW_CHAT, CREW_GOAL_ACHIEVED, MANAGER_CHANGED,
-- BACKGROUND_UNLOCKED, REPORT_RESOLVED)
ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type
    CHECK (type IN ('PLAN_MISSED', 'BODY_SHAPE_CHANGED', 'JOIN_REQUESTED', 'JOIN_DECIDED',
                    'JOIN_EXPIRED', 'LEADER_TRANSFERRED', 'CREW_DELETED', 'KICKED',
                    'CREW_RUN_RECRUIT', 'CREW_RUN_SOON', 'OPPONENT_ARRIVED', 'BATTLE_CORRECTED',
                    'CREW_NOTICE', 'CREW_POLL', 'POLL_CLOSING_SOON', 'CREW_SCHEDULE', 'CREW_CHAT',
                    'CREW_GOAL_ACHIEVED', 'MANAGER_CHANGED', 'BACKGROUND_UNLOCKED', 'REPORT_RESOLVED'));

-- 감사 행위 + 5종(운영진 지정·해제, 채팅 강제 삭제, 신고 숨김·기각)
ALTER TABLE audit_record DROP CONSTRAINT ck_audit_record_action;
ALTER TABLE audit_record ADD CONSTRAINT ck_audit_record_action
    CHECK (action IN ('SIGN_UP_CONSENT', 'CONSENT_CHANGE', 'ROUTE_DISPOSAL', 'WITHDRAWAL',
                      'LEADER_TRANSFER', 'CREW_DELETE', 'KICK', 'POST_FORCE_DELETE',
                      'CELEBRITY_REGISTER', 'CELEBRITY_DELETE', 'SETTING_CHANGE',
                      'OPERATOR_LOGIN_SUCCESS', 'OPERATOR_LOGIN_FAILURE', 'RANKING_RECOMPUTED',
                      'MANAGER_APPOINT', 'MANAGER_DISMISS', 'CHAT_FORCE_DELETE', 'REPORT_HIDE',
                      'REPORT_DISMISS'));

-- 감사 행위자 역할 + CREW_MANAGER
ALTER TABLE audit_record DROP CONSTRAINT ck_audit_record_actor_role;
ALTER TABLE audit_record ADD CONSTRAINT ck_audit_record_actor_role
    CHECK (actor_role IN ('RUNNER', 'CREW_LEADER', 'CREW_MANAGER', 'OPERATOR', 'SYSTEM'));

-- 크루 역할 + MANAGER(운영진)
ALTER TABLE crew_membership DROP CONSTRAINT ck_crew_membership_role;
ALTER TABLE crew_membership ADD CONSTRAINT ck_crew_membership_role
    CHECK (role IN ('LEADER', 'MANAGER', 'MEMBER'));

-- 게시 종류 + MEETUP·NOTICE·POLL·BOARD
ALTER TABLE crew_post DROP CONSTRAINT ck_crew_post_kind;
ALTER TABLE crew_post ADD CONSTRAINT ck_crew_post_kind
    CHECK (post_kind IN ('CHEER', 'CREW_RUN', 'MEETUP', 'NOTICE', 'POLL', 'BOARD'));

-- 크루런 참석 응답 + UNDECIDED(AttendanceAnswer, 기존 값 GOING·NOT_GOING 그대로)
ALTER TABLE crew_run_participation DROP CONSTRAINT ck_crew_run_participation_answer;
ALTER TABLE crew_run_participation ADD CONSTRAINT ck_crew_run_participation_answer
    CHECK (answer IN ('GOING', 'NOT_GOING', 'UNDECIDED'));
