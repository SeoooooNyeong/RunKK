-- 크루 상세의 누적 크루런 횟수·최근 크루런 3건(피그마 F-02, S15P31A207-295): 크루마다 마친 크루런을 약속 시각 최신순으로 읽는다.
-- 지운 글·마치지 않은 크루런은 색인에 넣지 않는다.
CREATE INDEX idx_crew_post_crew_run_finished ON crew_post (crew_id, scheduled_at DESC)
    WHERE post_kind = 'CREW_RUN' AND status = 'FINISHED' AND NOT deleted;
