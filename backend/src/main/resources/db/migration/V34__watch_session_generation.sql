-- 회원별 워치 세션 세대(2026-11-01 사용자 결정 W25, 코드 리뷰 12차 U12-6). POST /auth/watch-session으로 발급한 갱신 토큰은 워치용 표시와
-- 발급 때의 세대를 담는다. 워치연동 끄기(DELETE /auth/watch-session)·로그아웃·탈퇴가 세대를 하나 올리면 그 전 세대의 워치용 갱신 토큰이 모두
-- 갱신에서 거부된다. 폰 갱신 토큰은 세대를 보지 않는다. 행이 없으면 세대 0이다. 토큰 원문은 저장하지 않는다.
CREATE TABLE watch_session_generation (
    member_id  BIGINT                      PRIMARY KEY,
    generation BIGINT                      NOT NULL,
    revoked_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
