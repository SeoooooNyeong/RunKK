-- v0.11 S1 회원·게스트·설정(부록 F F.5.3 V20, D-89·D-91·D-92·D-128·D-141·D-156).

-- 계정 유형(FR-197): 기존 회원은 모두 소셜 연동 회원이다.
ALTER TABLE member ADD COLUMN account_type VARCHAR(10) NOT NULL DEFAULT 'LINKED';
ALTER TABLE member ADD CONSTRAINT ck_member_account_type CHECK (account_type IN ('GUEST', 'LINKED'));

-- 마지막 접속일시(FR-200): 기존 행은 마지막 변경 일시로 채운 뒤 NOT NULL로 바꾼다.
ALTER TABLE member ADD COLUMN last_access_at TIMESTAMP(6);
UPDATE member SET last_access_at = updated_at;
ALTER TABLE member ALTER COLUMN last_access_at SET NOT NULL;

-- 게스트 기기 자격 증명 비밀의 SHA-256 16진 64자(D-89). 저장 뒤 붙이므로 NULL 허용(D-128).
ALTER TABLE member ADD COLUMN guest_credential_hash VARCHAR(64);

-- 화면 테마(FR-203)와 배틀런 근접 알림(FR-109, D-92)
ALTER TABLE member ADD COLUMN screen_theme VARCHAR(10) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE member ADD CONSTRAINT ck_member_screen_theme CHECK (screen_theme IN ('SYSTEM', 'LIGHT', 'DARK'));
ALTER TABLE member ADD COLUMN battle_proximity_alert BOOLEAN NOT NULL DEFAULT TRUE;

-- 러너 성향 태그 삭제(FR-119, D-91). 값은 옮기지 않는다(CR-04).
ALTER TABLE member DROP COLUMN tags;
ALTER TABLE crew_membership DROP COLUMN crew_tags;

-- 게스트는 소셜 계정이 없고, 연동 회원은 탈퇴 전까지 소셜 계정이 있다.
ALTER TABLE member ADD CONSTRAINT ck_member_account
    CHECK ((account_type = 'GUEST' AND provider IS NULL AND provider_account_id IS NULL)
        OR (account_type = 'LINKED'
            AND (status = 'WITHDRAWN' OR (provider IS NOT NULL AND provider_account_id IS NOT NULL))));

-- 탈퇴 행은 게스트 자격 증명 해시도 지운다.
ALTER TABLE member DROP CONSTRAINT ck_member_withdrawn;
ALTER TABLE member ADD CONSTRAINT ck_member_withdrawn
    CHECK ((status = 'ACTIVE' AND withdrawn_at IS NULL)
        OR (status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL AND nickname IS NULL
            AND provider IS NULL AND provider_account_id IS NULL AND guest_credential_hash IS NULL));

-- 미이용 게스트 파기 대상(WithdrawalService.withdrawIdleGuests, MemberRepository.findIdleGuests)
CREATE INDEX idx_member_idle_guest ON member (last_access_at) WHERE account_type = 'GUEST' AND status = 'ACTIVE';
