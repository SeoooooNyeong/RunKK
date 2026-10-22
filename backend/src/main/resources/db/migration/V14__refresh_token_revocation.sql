-- 갱신 토큰 폐기 목록(코드 리뷰 2차 M6 후속 결정). 로그아웃했거나 이미 한 번 쓴 갱신 토큰 ID(jti)를 토큰 만료까지 둔다.
-- Redis 장애·재시작과 무관하게 남고, 기본 키 하나로 "처음 쓰기"를 원자적으로 정한다. 토큰 원문은 저장하지 않는다.
CREATE TABLE refresh_token_revocation (
    jti        VARCHAR(36)                 PRIMARY KEY,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

-- 만료 행 정리(batch.member RevocationPurgeJob)
CREATE INDEX idx_refresh_token_revocation_expires_at ON refresh_token_revocation (expires_at);
