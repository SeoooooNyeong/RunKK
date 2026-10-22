-- 운영자 로그인 감사(코드 리뷰 2차 L25): 행위 종류에 OPERATOR_LOGIN_SUCCESS·OPERATOR_LOGIN_FAILURE를 더한다.
-- 행위자 = 운영자 계정(확인 못 하면 UNKNOWN), 대상 = 접속지, 변경 후 = 결과(SUCCESS 또는 실패 사유).
ALTER TABLE audit_record DROP CONSTRAINT ck_audit_record_action;
ALTER TABLE audit_record ADD CONSTRAINT ck_audit_record_action
    CHECK (action IN ('SIGN_UP_CONSENT', 'CONSENT_CHANGE', 'ROUTE_DISPOSAL', 'WITHDRAWAL',
                      'LEADER_TRANSFER', 'CREW_DELETE', 'KICK', 'POST_FORCE_DELETE',
                      'CELEBRITY_REGISTER', 'CELEBRITY_DELETE', 'SETTING_CHANGE',
                      'OPERATOR_LOGIN_SUCCESS', 'OPERATOR_LOGIN_FAILURE'));
