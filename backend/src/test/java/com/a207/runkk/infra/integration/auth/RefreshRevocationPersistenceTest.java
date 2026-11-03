package com.a207.runkk.infra.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.TokenPair;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 코드 리뷰 2차 M6과 후속 결정: 폐기한 갱신 토큰 목록이 PostgreSQL(V14)에 남아 재시작(같은 저장소를 쓰는 새 발급기)과 Redis 장애에 영향받지 않고,
 * 같은 토큰을 동시에 써도 한 번만 통과하며, 만료 정리는 만료된 행만 지운다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("갱신 토큰 폐기 목록(PostgreSQL)")
class RefreshRevocationPersistenceTest {

    private static final String SECRET = "persist-secret-persist-secret-0032";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int RACERS = 8;

    @Autowired JdbcRefreshTokenRevocations store;
    @Autowired JdbcWatchSessionGenerations watchSessions;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from refresh_token_revocation");
    }

    private JwtTokenIssuer started(Clock clock) {
        return new JwtTokenIssuer(SECRET, clock, store, watchSessions);
    }

    private JwtTokenIssuer started() {
        return started(Clock.system(SEOUL));
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
    @DisplayName("한 번 쓴 갱신 토큰은 재시작한 뒤에도 다시 쓸 수 없고, 행의 만료 시각은 토큰 만료와 같다")
    void redeemedToken_cannotBeReplayedAfterRestart() {
        Instant now = Instant.now();
        TokenPair pair = started(Clock.fixed(now, SEOUL)).issue(8);
        assertThat(started().redeemRefreshToken(pair.refreshToken()).memberId()).isEqualTo(8);

        JwtTokenIssuer restarted = started();
        assertThatThrownBy(() -> restarted.redeemRefreshToken(pair.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
        Instant expires =
                jdbc.queryForObject(
                                "select expires_at from refresh_token_revocation", Timestamp.class)
                        .toInstant();
        assertThat(expires.getEpochSecond())
                .isEqualTo(now.plus(JwtTokenIssuer.REFRESH_TTL).getEpochSecond());
    }

    @Test
    @DisplayName("같은 갱신 토큰을 동시에 8번 써도 한 번만 통과한다(고유 키 한 번으로 정한다)")
    void concurrentRedeem_onlyOneWins() throws Exception {
        TokenPair pair = started().issue(9);
        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < RACERS; i++) {
            results.add(
                    pool.submit(
                            () -> {
                                go.await();
                                try {
                                    started().redeemRefreshToken(pair.refreshToken());
                                    return true;
                                } catch (AuthFailureException e) {
                                    return false;
                                }
                            }));
        }
        go.countDown();
        pool.shutdown();
        int wins = 0;
        for (Future<Boolean> f : results) {
            wins += f.get() ? 1 : 0;
        }
        assertThat(wins).isEqualTo(1);
    }

    @Test
    @DisplayName("만료 정리는 토큰 만료가 지난 행만 지운다")
    void purge_removesOnlyExpiredRows() {
        Instant t0 = Instant.parse("2026-10-01T00:00:00Z");
        JwtTokenIssuer old = started(Clock.fixed(t0, SEOUL));
        old.revoke(old.issue(1).refreshToken());
        JwtTokenIssuer fresh = started();
        String live = fresh.issue(2).refreshToken();
        fresh.revoke(live);

        LocalDateTime oldExpiry =
                LocalDateTime.ofInstant(t0.plus(JwtTokenIssuer.REFRESH_TTL), SEOUL);
        assertThat(fresh.purgeExpiredRevocations(oldExpiry.minusSeconds(1))).isZero();
        assertThat(fresh.purgeExpiredRevocations(LocalDateTime.now(SEOUL))).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from refresh_token_revocation", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> fresh.redeemRefreshToken(live))
                .isInstanceOf(AuthFailureException.class);
    }
}
