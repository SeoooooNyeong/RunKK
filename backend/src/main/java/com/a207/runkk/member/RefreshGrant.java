package com.a207.runkk.member;

import java.util.OptionalLong;

/**
 * 갱신 토큰을 쓴(회전한) 결과(D-19, W25). TokenIssuer.reissue가 같은 종류의 새 토큰 쌍을 만들 때 쓴다. 워치용 갱신 토큰(POST
 * /auth/watch-session으로 발급했거나 그것을 회전한 것)이면 발급 때의 워치 세션 세대를 담는다. 새 쌍이 이 세대를 그대로 이어 받으므로 갱신과 일괄
 * 폐기(revokeWatchSessions)가 겹쳐도 폐기 전 세대의 토큰이 살아남지 않는다.
 *
 * @param memberId 회원 ID
 * @param watchGeneration 워치용이면 워치 세션 세대, 폰 토큰이면 비어 있다
 */
public record RefreshGrant(long memberId, OptionalLong watchGeneration) {

    public RefreshGrant {
        if (watchGeneration == null) {
            throw new IllegalArgumentException(
                    "watchGeneration은 null일 수 없다(폰 토큰은 OptionalLong.empty())");
        }
    }

    /** 폰(로그인·게스트 재개)에서 발급한 갱신 토큰. */
    public static RefreshGrant phone(long memberId) {
        return new RefreshGrant(memberId, OptionalLong.empty());
    }

    /** 워치용 갱신 토큰. */
    public static RefreshGrant watch(long memberId, long generation) {
        return new RefreshGrant(memberId, OptionalLong.of(generation));
    }

    public boolean isWatch() {
        return watchGeneration.isPresent();
    }
}
