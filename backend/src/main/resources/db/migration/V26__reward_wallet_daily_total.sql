-- v0.11 S4 리워드 집계 칼럼(부록 F F.5.3 V26, D-109, D-123). 지갑이 적립 내역 전체를 읽지 않도록 가장 최근 적립일과 그날 합계만 둔다.
-- 지난 날짜(보정 배치가 원래 사건일로 센다) 합계는 idx_reward_accrual_member_accrued로 reward_accrual에서 센다.

ALTER TABLE reward_wallet ADD COLUMN last_accrual_date DATE;
ALTER TABLE reward_wallet ADD COLUMN last_accrual_date_amount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reward_wallet ADD CONSTRAINT ck_reward_wallet_last_amount CHECK (last_accrual_date_amount >= 0);

-- 기존 행: 회원별 가장 최근 적립일(사건 발생일)과 그날 합계로 채운다
UPDATE reward_wallet w
SET last_accrual_date        = latest.day,
    last_accrual_date_amount = latest.total
FROM (SELECT a.member_id, d.day, SUM(a.amount) AS total
      FROM reward_accrual a
               JOIN (SELECT member_id, MAX(CAST(accrued_at AS DATE)) AS day
                     FROM reward_accrual
                     GROUP BY member_id) d
                    ON d.member_id = a.member_id AND CAST(a.accrued_at AS DATE) = d.day
      GROUP BY a.member_id, d.day) latest
WHERE w.member_id = latest.member_id;

-- 과거 날짜 합계 조회(RewardAccrualRepository.sumOn)
CREATE INDEX idx_reward_accrual_member_accrued ON reward_accrual (member_id, accrued_at);
