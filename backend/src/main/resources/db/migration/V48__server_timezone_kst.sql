-- 서버 시간대 수정(DF-073, S15P31A207-275). 서버가 쓴 시각을 9시간 되돌려 DB 저장 값을 실제 한국 시각으로 맞춘다.
--
-- 원인: API 컨테이너 JVM 시간대가 UTC였고 hibernate.jdbc.time_zone은 Asia/Seoul이었다. 서버가 JPA(Hibernate)로 쓴 LocalDateTime(한국 시각)은
-- DB에 9시간 앞선 값으로 저장되고 읽을 때 9시간 빠져 앱에서는 맞게 보였다. 이 판부터 JVM 시간대를 Asia/Seoul로 맞추고(RunkkApplication.main,
-- Dockerfile, compose) jdbc.time_zone을 지워 LocalDateTime을 그대로 저장한다. 그래서 이미 저장된 "서버가 쓴 값"만 9시간 뺀다.
--
-- 이 마이그레이션은 새 설정(JVM Asia/Seoul)과 같은 이미지로만 배포한다. 옛 설정의 api가 이 마이그레이션 뒤에 다시 쓰면 그 값은 또 9시간 앞선다.
-- 순서 밖 적용 주의: develop에서 이 V48을 적용한 뒤 같은 내용을 다른 번호(예: 핫픽스 V35_4)로 main에 넣고 역병합하면 develop이 두 번 뺀다.
-- 운영은 2차 배포(V36~V48을 차례로 적용)로 받는다. 따로 빨리 넣어야 하면 이 파일을 옮기지 말고 설계 05-28 6절 절차를 따른다.
--
-- 되돌리지 않는 것(근거는 design-oo/05-28-설계-서버시간대-성.md 4절)
--   timestamp with time zone 열(refresh_token_revocation.expires_at, watch_session_generation.revoked_at, analytics 스키마): 시점 값이라 영향 없음
--   match_ticket.waiting_since, assigned_at: waiting_since는 JdbcTemplate(Timestamp.valueOf)로 써서 옮겨지지 않았고, assigned_at은 SQL
--                     LOCALTIMESTAMP(옛 세션 UTC)라 UTC 벽시계 값이다. 대기열은 잠깐만 남는 행이고(두 환경 0행) 배정 뒤 5분 정리가 지운다
--   flyway_schema_history.installed_on: Flyway 기록이라 고치지 않는다(옛 행은 UTC 벽시계 값으로 남는다)
--   celebrity_record: V32 시드 10건(리터럴)과 운영자가 SQL로 넣은 11, 12번(DB now(), 한국 시각)은 이미 한국 시각이다.
--                     관리 API로 등록한 행(감사 CELEBRITY_REGISTER가 있는 행)만 되돌린다
--   event.created_at, updated_at: V35_2 시드는 Flyway 연결(세션 시간대 UTC)의 now()라 UTC 벽시계 값(한국 시각보다 9시간 이름)이다. 시드 행은
--                     9시간 더하고, 관리 API로 만든 행과 바꾼 값은 9시간 뺀다
--   event.starts_at, ends_at: V35_2 시드는 한국 시각 리터럴이었지만 2026-11-09 임시 보정(+9시간)으로 서버 기준 값이 되었고, 관리 API로 만든 행도
--                     서버 기준 값이라 9시간 뺀다
--   event 시드가 보정 전 리터럴(11-08 00:00 ~ 11-16 23:59:59) 그대로인 DB: 옛 설정 api가 쓴 적 없는 새 DB다(시험 컨테이너, 새 환경. 새 설정 이미지가
--                     V35_2와 V48을 한 번에 적용해 시드 now()도 한국 시각이다). 그 시드 행은 기간, 만든 시각, 고친 시각을 모두 그대로 둔다

-- 1. 감사 기록은 추가 전용(V1 트리거)이라 이 마이그레이션 안에서만 트리거를 끄고 고친다
ALTER TABLE audit_record DISABLE TRIGGER trg_audit_record_reject_update;
UPDATE audit_record SET recorded_at = recorded_at - INTERVAL '9 hours';
ALTER TABLE audit_record ENABLE TRIGGER trg_audit_record_reject_update;

-- 2. 유명인 기록: 관리 API로 등록한 행만(1번의 감사 기록과 시각을 고치기 전후와 관계없이 target_id로 찾는다)
UPDATE celebrity_record c
SET registered_at          = c.registered_at - INTERVAL '9 hours',
    real_name_consented_at = c.real_name_consented_at - INTERVAL '9 hours'
WHERE EXISTS (SELECT 1 FROM audit_record a
              WHERE a.action = 'CELEBRITY_REGISTER' AND a.target_id = c.id::text);

-- 3. 이벤트: 보정 전 시드(새 DB)를 빼고 기간은 모두, 만든 시각과 고친 시각은 시드와 서버 값을 나눈다(같은 UPDATE 안의 식은 고치기 전 값을 본다)
UPDATE event
SET starts_at  = starts_at - INTERVAL '9 hours',
    ends_at    = ends_at - INTERVAL '9 hours',
    created_at = CASE WHEN slug IN ('coach-challenge', 'beta-1km')
                      THEN created_at + INTERVAL '9 hours'
                      ELSE created_at - INTERVAL '9 hours' END,
    updated_at = CASE WHEN slug IN ('coach-challenge', 'beta-1km') AND updated_at = created_at
                      THEN updated_at + INTERVAL '9 hours'
                      ELSE updated_at - INTERVAL '9 hours' END
WHERE NOT (slug IN ('coach-challenge', 'beta-1km')
           AND starts_at = TIMESTAMP '2026-11-08 00:00:00'
           AND ends_at = TIMESTAMP '2026-11-16 23:59:59');

-- 4. 그 밖에 서버가 JPA로 쓰는 timestamp without time zone 열 전부(V47 기준 public 스키마, 표 이름순). NULL은 NULL로 남는다
UPDATE ai_purge_request SET lease_until = lease_until - INTERVAL '9 hours',
                            next_at = next_at - INTERVAL '9 hours',
                            requested_at = requested_at - INTERVAL '9 hours';
UPDATE avatar_background SET unlocked_at = unlocked_at - INTERVAL '9 hours';
UPDATE battle SET closed_at = closed_at - INTERVAL '9 hours',
                  countdown_ends_at = countdown_ends_at - INTERVAL '9 hours',
                  created_at = created_at - INTERVAL '9 hours',
                  judged_at = judged_at - INTERVAL '9 hours',
                  max_ends_at = max_ends_at - INTERVAL '9 hours',
                  opponent_arrived_at = opponent_arrived_at - INTERVAL '9 hours',
                  opponent_arrives_at = opponent_arrives_at - INTERVAL '9 hours',
                  start_at = start_at - INTERVAL '9 hours';
UPDATE battle_disconnection SET disconnected_at = disconnected_at - INTERVAL '9 hours',
                                reconnected_at = reconnected_at - INTERVAL '9 hours';
UPDATE battle_participant SET arrived_at = arrived_at - INTERVAL '9 hours';
UPDATE coach_conversation SET last_asked_at = last_asked_at - INTERVAL '9 hours';
UPDATE coach_daily_usage SET lease_until = lease_until - INTERVAL '9 hours';
UPDATE content_report SET reported_at = reported_at - INTERVAL '9 hours';
UPDATE course_verdict SET judged_at = judged_at - INTERVAL '9 hours';
UPDATE crew SET created_at = created_at - INTERVAL '9 hours',
                invite_code_expires_at = invite_code_expires_at - INTERVAL '9 hours';
UPDATE crew_chat_message SET sent_at = sent_at - INTERVAL '9 hours';
UPDATE crew_chat_reaction SET reacted_at = reacted_at - INTERVAL '9 hours';
UPDATE crew_meetup_attendance SET answered_at = answered_at - INTERVAL '9 hours';
UPDATE crew_membership SET joined_at = joined_at - INTERVAL '9 hours',
                           left_at = left_at - INTERVAL '9 hours';
UPDATE crew_photo SET created_at = created_at - INTERVAL '9 hours';
UPDATE crew_poll_response SET responded_at = responded_at - INTERVAL '9 hours';
UPDATE crew_post SET created_at = created_at - INTERVAL '9 hours',
                     departed_at = departed_at - INTERVAL '9 hours',
                     ended_at = ended_at - INTERVAL '9 hours',
                     poll_closes_at = poll_closes_at - INTERVAL '9 hours',
                     scheduled_at = scheduled_at - INTERVAL '9 hours',
                     vote_deadline = vote_deadline - INTERVAL '9 hours';
UPDATE crew_run_participation SET voted_at = voted_at - INTERVAL '9 hours';
UPDATE disposal_order SET device_confirmed_at = device_confirmed_at - INTERVAL '9 hours',
                          due_at = due_at - INTERVAL '9 hours',
                          executed_at = executed_at - INTERVAL '9 hours';
UPDATE event_point_grant SET granted_at = granted_at - INTERVAL '9 hours';
UPDATE face_part_image SET created_at = created_at - INTERVAL '9 hours';
UPDATE friend_invite SET closed_at = closed_at - INTERVAL '9 hours',
                         created_at = created_at - INTERVAL '9 hours',
                         expires_at = expires_at - INTERVAL '9 hours';
UPDATE join_request SET decided_at = decided_at - INTERVAL '9 hours',
                        requested_at = requested_at - INTERVAL '9 hours';
UPDATE member SET created_at = created_at - INTERVAL '9 hours',
                  joined_at = joined_at - INTERVAL '9 hours',
                  last_access_at = last_access_at - INTERVAL '9 hours',
                  updated_at = updated_at - INTERVAL '9 hours',
                  withdrawn_at = withdrawn_at - INTERVAL '9 hours';
UPDATE member_consent SET changed_at = changed_at - INTERVAL '9 hours';
UPDATE notification SET occurred_at = occurred_at - INTERVAL '9 hours';
UPDATE operation_setting SET updated_at = updated_at - INTERVAL '9 hours';
UPDATE operation_setting_history SET changed_at = changed_at - INTERVAL '9 hours';
UPDATE owned_item SET purchased_at = purchased_at - INTERVAL '9 hours';
UPDATE placement_progress SET completed_at = completed_at - INTERVAL '9 hours';
UPDATE placement_round SET counted_at = counted_at - INTERVAL '9 hours';
UPDATE push_device SET registered_at = registered_at - INTERVAL '9 hours';
UPDATE ranking_snapshot SET computed_at = computed_at - INTERVAL '9 hours';
UPDATE report SET created_at = created_at - INTERVAL '9 hours';
UPDATE report_case SET opened_at = opened_at - INTERVAL '9 hours',
                       resolved_at = resolved_at - INTERVAL '9 hours';
UPDATE reward_accrual SET accrued_at = accrued_at - INTERVAL '9 hours';
UPDATE run_record SET ended_at = ended_at - INTERVAL '9 hours',
                      saved_at = saved_at - INTERVAL '9 hours',
                      started_at = started_at - INTERVAL '9 hours';
UPDATE run_record_tombstone SET deleted_at = deleted_at - INTERVAL '9 hours';
UPDATE runbody_job SET created_at = created_at - INTERVAL '9 hours',
                       next_poll_at = next_poll_at - INTERVAL '9 hours',
                       run_started_at = run_started_at - INTERVAL '9 hours',
                       submitted_at = submitted_at - INTERVAL '9 hours';
UPDATE runimal_profile SET judged_at = judged_at - INTERVAL '9 hours';
UPDATE runimal_type_share SET computed_at = computed_at - INTERVAL '9 hours';
UPDATE selfie_job SET attempted_at = attempted_at - INTERVAL '9 hours';
UPDATE weekly_race SET achieved_at = achieved_at - INTERVAL '9 hours';
