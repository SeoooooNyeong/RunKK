-- D-07 대안 대기열: 배정 시각. 배정 직후 장애로 남은 ASSIGNED 행이 재요청을 영구히 막지 않게
-- 5분 넘은 배정 행은 같은 회원이 다시 대기할 때 지운다(Redis 표 수명 5분과 같다, 코드 리뷰 2차 L11).
ALTER TABLE match_ticket ADD COLUMN assigned_at TIMESTAMP(6);
