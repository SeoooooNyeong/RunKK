package com.a207.runkk.infra.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.TokenPair;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 코드 리뷰 2차 M6: 폐기한 갱신 토큰 목록이 재시작 뒤에도 남는다(Redis, 만료 = 토큰의 남은 수명). 재시작은 같은 Redis를 쓰는 새 발급기 인스턴스로 흉내
 * 낸다(test 프로필 내장 Redis).
 */
@SpringBootTest(classes = RunkkApplication.class)
@ActiveProfiles("test")
@DisplayName("갱신 토큰 폐기 목록 영속(M6)")
class RefreshRevocationPersistenceTest {

    private static final String SECRET = "persist-secret-persist-secret-0032";

    @Autowired StringRedisTemplate redis;

    private JwtTokenIssuer started() {
        Clock clock = Clock.systemUTC();
        return new JwtTokenIssuer(SECRET, clock, new RefreshTokenRevocations(redis, clock));
    }

    @Test
    @DisplayName("로그아웃으로 폐기한 갱신 토큰은 재시작한 뒤에도 쓸 수 없다")
    void revokedToken_staysRevokedAfterRestart() {
        TokenPair pair = started().issue(7);
        started().revoke(pair.refreshToken());

        JwtTokenIssuer restarted = started();
        assertThatThrownBy(() -> restarted.redeemRefreshToken(pair.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    @DisplayName("한 번 쓴 갱신 토큰은 재시작한 뒤에도 다시 쓸 수 없고, 폐기 키는 토큰 만료와 함께 사라진다")
    void redeemedToken_cannotBeReplayedAfterRestart() {
        TokenPair pair = started().issue(8);
        assertThat(started().redeemRefreshToken(pair.refreshToken())).isEqualTo(8);

        JwtTokenIssuer restarted = started();
        assertThatThrownBy(() -> restarted.redeemRefreshToken(pair.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
        assertThat(redis.keys(RefreshTokenRevocations.KEY_PREFIX + "*"))
                .allSatisfy(
                        key ->
                                assertThat(redis.getExpire(key))
                                        .isPositive()
                                        .isLessThanOrEqualTo(
                                                JwtTokenIssuer.REFRESH_TTL.toSeconds()));
    }
}
