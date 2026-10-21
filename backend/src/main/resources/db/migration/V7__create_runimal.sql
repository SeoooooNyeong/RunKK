-- runimal 패키지 소유 테이블(설계 5.1.1, D11 캐릭터 파일의 유형 부분, FR-166 코스 판정).

CREATE TABLE runimal_profile (
    member_id        BIGINT PRIMARY KEY,
    valid_run_count  INTEGER NOT NULL,
    provisional_type VARCHAR(20),
    confirmed_type   VARCHAR(20),
    companion_ratio  DOUBLE PRECISION,
    distance_ratio   DOUBLE PRECISION,
    course_ratio     DOUBLE PRECISION,
    habit_ratio      DOUBLE PRECISION,
    judged_at        TIMESTAMP(6),
    version          BIGINT  NOT NULL,
    CONSTRAINT ck_runimal_profile_count CHECK (valid_run_count >= 0)
);

CREATE TABLE course_verdict (
    record_id   VARCHAR(36)  PRIMARY KEY,
    member_id   BIGINT       NOT NULL,
    same_course BOOLEAN      NOT NULL,
    judged_at   TIMESTAMP(6) NOT NULL
);

-- 탈퇴 삭제(FR-021)
CREATE INDEX idx_course_verdict_member ON course_verdict (member_id);
