-- 서버 시간대 수정 후속(DF-073, S15P31A207-275, 코드 리뷰 [임시:TZ-성-1]). 보정 없는 새 DB에서 V48이 틀어 놓은 이벤트 시드를 바로잡는다.
--
-- V48은 이벤트 기간을 조건 없이 9시간 빼고 시드 만든 시각을 9시간 더한다. 2026-11-09 임시 보정(+9)이 된 DB(develop, 운영)에서만 맞다.
-- 보정 없는 새 DB(시험 컨테이너, 새 환경, 덤프 없이 다시 만드는 DB)는 새 설정 이미지가 V35_2와 V48을 한 번에 적용하므로
-- 시드 기간이 2026-11-07 15:00:00 ~ 2026-11-16 14:59:59가 되고(이벤트가 9시간 일찍 끝남) 만든 시각은 9시간 미래가 된다.
-- V48은 리뷰 수정 전 판(edcdda80)이 develop에 이미 적용되어(2026-11-09 16:12) 내용을 바꾸면 Flyway 체크섬 검증이 실패하므로 고치지 않고,
-- 그 결과를 이 마이그레이션이 되돌린다(설계 design-oo/05-28-설계-서버시간대-성.md 4.2).
--
-- 대상: 시드 두 행 중 기간이 「보정 전 리터럴에서 9시간 뺀 값」 그대로인 행만.
--   기간: 9시간 더해 V35_2 리터럴(11-08 00:00:00 ~ 11-16 23:59:59, 한국 시각)로 돌린다.
--   만든 시각: V48 적용 시각보다 뒤(미래)이면 새 설정 세션(한국 시각)의 now()에 V48이 9시간 더한 값이라 9시간 뺀다.
--             고친 시각은 만든 시각과 같을 때만(V48 뒤 관리 API로 고치지 않음) 같이 뺀다.
--             V48 적용 시각보다 앞이면(옛 설정 세션 UTC로 V35_2를 적용했고 보정만 없던 DB) V48이 맞게 고친 값이라 그대로 둔다.
-- develop과 운영은 임시 보정 상태라 V48 뒤 기간이 11-08 00:00:00 ~ 11-16 23:59:59이므로 이 조건에 걸리지 않고 아무것도 바뀌지 않는다.
-- (운영 임시 보정이 2차 배포 전에 풀려 있더라도 기간만 리터럴로 돌아가고 만든 시각은 V48 값 그대로라 결과가 맞다.)

UPDATE event
SET starts_at  = starts_at + INTERVAL '9 hours',
    ends_at    = ends_at + INTERVAL '9 hours',
    created_at = CASE WHEN created_at > (SELECT installed_on FROM flyway_schema_history WHERE version = '48')
                      THEN created_at - INTERVAL '9 hours'
                      ELSE created_at END,
    updated_at = CASE WHEN created_at > (SELECT installed_on FROM flyway_schema_history WHERE version = '48')
                           AND updated_at = created_at
                      THEN updated_at - INTERVAL '9 hours'
                      ELSE updated_at END
WHERE slug IN ('coach-challenge', 'beta-1km')
  AND starts_at = TIMESTAMP '2026-11-07 15:00:00'
  AND ends_at = TIMESTAMP '2026-11-16 14:59:59';
