package com.a207.runkk.member;

/**
 * 갱신 토큰을 쓴(회전한) 결과(D-19, W25). TokenIssuer.reissue가 같은 종류의 새 토큰 쌍을 만들 때 쓴다. 폰 갱신 토큰이면 Phone, 워치용 갱신
 * 토큰(POST /auth/watch-session으로 발급했거나 그것을 회전한 것)이면 발급 때의 워치 세션 세대를 담은 Watch다. 새 쌍이 이 세대를 그대로 이어
 * 받으므로 갱신과 일괄 폐기(revokeWatchSessions)가 겹쳐도 폐기 전 세대의 토큰이 살아남지 않는다. 종류를 타입으로 나눠 reissue가 값
 * 유무(Optional)를 따지지 않는다(코드 리뷰 13차 BE13-1, 설계 검증 R74).
 */
public sealed interface RefreshGrant {

    /** 회원 ID. */
    long memberId();

    /** 폰(로그인·게스트 재개)에서 발급한 갱신 토큰. */
    record Phone(long memberId) implements RefreshGrant {}

    /**
     * 워치용 갱신 토큰.
     *
     * @param generation 발급 때의 워치 세션 세대
     */
    record Watch(long memberId, long generation) implements RefreshGrant {}
}
