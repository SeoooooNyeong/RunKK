-- 크루런 실시간 반영(설계 05-32, S15P31A207-306). 전부 기존 행을 깨지 않는 추가다(옛 진행 참가자의 시작 시각, 측정 기록 ID는 비워 두고 서버가
-- 출발 시각, 연결 기록으로 읽는다).
-- crew_post: 출발 카운트다운 예약(한 크루런에 하나, [임시:D-성-881])과 변경 순번([임시:D-성-878]). 순번은 크루런 글만 쓰고 다른 글은 0이다.
ALTER TABLE crew_post
    ADD COLUMN countdown_token      VARCHAR(40),
    ADD COLUMN countdown_planned_at TIMESTAMP(6),
    ADD COLUMN countdown_start_at   TIMESTAMP(6),
    ADD COLUMN start_reason         VARCHAR(12),
    ADD COLUMN change_revision      BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_crew_post_start_reason
        CHECK (start_reason IS NULL OR start_reason IN ('ALL_READY', 'HOST', 'SCHEDULED')),
    ADD CONSTRAINT ck_crew_post_countdown
        CHECK ((countdown_token IS NULL) = (countdown_start_at IS NULL));

-- crew_run_participation: 출발 후보 고정([임시:D-성-882])과 개인 측정 시작 시각, 서버 예약 측정 기록 ID([임시:D-성-884]).
ALTER TABLE crew_run_participation
    ADD COLUMN countdown_candidate   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN joined_at             TIMESTAMP(6),
    ADD COLUMN measurement_record_id VARCHAR(36);

-- 진행 중 크루런 날짜순 목록((예정일시, ID) 오름차순, 설계 05-32 4.5)
CREATE INDEX idx_crew_post_crew_run_scheduled ON crew_post (crew_id, scheduled_at, id)
    WHERE post_kind = 'CREW_RUN' AND NOT deleted;

-- 기여 도착 때 그 기록을 이은 크루런 찾기(RESULT 신호)
CREATE INDEX idx_crew_run_participation_linked ON crew_run_participation (linked_record_id)
    WHERE linked_record_id IS NOT NULL;
