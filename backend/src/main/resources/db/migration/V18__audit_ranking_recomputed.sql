-- 운영자 랭킹 다시 계산 감사: 행위 종류에 RANKING_RECOMPUTED를 더한다(POST /admin/rankings/recompute).
-- 행위자 = 운영자 계정, 대상 = RANKING, 변경 후 = 집계 시각·개인·크루 건수·저장 실패 수.
ALTER TABLE audit_record DROP CONSTRAINT ck_audit_record_action;
ALTER TABLE audit_record ADD CONSTRAINT ck_audit_record_action
    CHECK (action IN ('SIGN_UP_CONSENT', 'CONSENT_CHANGE', 'ROUTE_DISPOSAL', 'WITHDRAWAL',
                      'LEADER_TRANSFER', 'CREW_DELETE', 'KICK', 'POST_FORCE_DELETE',
                      'CELEBRITY_REGISTER', 'CELEBRITY_DELETE', 'SETTING_CHANGE',
                      'OPERATOR_LOGIN_SUCCESS', 'OPERATOR_LOGIN_FAILURE', 'RANKING_RECOMPUTED'));
