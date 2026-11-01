package com.a207.runkk.infra.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.ProviderUnavailableException;
import com.a207.runkk.member.RefreshGrant;
import com.a207.runkk.member.TokenPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 워치용 갱신 토큰(2026-11-01 사용자 결정 W25, 코드 리뷰 12차 U12-6): POST /auth/watch-session으로 발급한 갱신 토큰은 워치용 표시와
 * 그 회원의 워치 세션 세대를 담는다. 회전해도 워치용이고 세대를 그대로 잇는다. revokeWatchSessions가 세대를 올리면 그 회원의 워치용 갱신 토큰이 모두
 * 거부되고 폰 토큰은 그대로다.
 */
@DisplayName("JwtTokenIssuer 워치용 갱신 토큰과 일괄 폐기")
class WatchSessionTokenTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-32";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant T0 = Instant.parse("2026-11-01T06:00:00Z");

    private final InMemoryRefreshTokenRevocations revocations =
            new InMemoryRefreshTokenRevocations();
    private final InMemoryWatchSessionGenerations generations =
            new InMemoryWatchSessionGenerations();
    private final JwtTokenIssuer issuer =
            new JwtTokenIssuer(SECRET, Clock.fixed(T0, SEOUL), revocations, generations);

    @Test
    @DisplayName("워치용 갱신 토큰은 워치용으로, 폰 토큰은 폰용으로 쓰이고 회전해도 종류가 이어진다")
    void watchMark_survivesRotation() {
        RefreshGrant phone = issuer.redeemRefreshToken(issuer.issue(7).refreshToken());
        RefreshGrant watch = issuer.redeemRefreshToken(issuer.issueWatch(7).refreshToken());

        assertThat(phone.memberId()).isEqualTo(7);
        assertThat(phone.isWatch()).isFalse();
        assertThat(watch.memberId()).isEqualTo(7);
        assertThat(watch.isWatch()).isTrue();
        assertThat(issuer.redeemRefreshToken(issuer.reissue(watch).refreshToken()).isWatch())
                .isTrue();
        assertThat(issuer.redeemRefreshToken(issuer.reissue(phone).refreshToken()).isWatch())
                .isFalse();
    }

    @Test
    @DisplayName("워치 접근 토큰은 폰 접근 토큰과 같이 검증된다")
    void watchAccessToken_verifies() {
        assertThat(issuer.verify(issuer.issueWatch(7).accessToken())).isEqualTo(7);
    }

    @Test
    @DisplayName(
            "revokeWatchSessions 뒤: 그 회원의 워치용 갱신 토큰(회전한 것 포함)은 거부, 폰 토큰과 다른 회원은 그대로, 새 발급은 쓸 수 있다")
    void revokeWatchSessions_rejectsOnlyThatMembersWatchTokens() {
        TokenPair phone = issuer.issue(7);
        TokenPair unused = issuer.issueWatch(7);
        TokenPair rotated =
                issuer.reissue(issuer.redeemRefreshToken(issuer.issueWatch(7).refreshToken()));
        TokenPair others = issuer.issueWatch(8);

        issuer.revokeWatchSessions(7);

        assertThatThrownBy(() -> issuer.redeemRefreshToken(unused.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
        assertThatThrownBy(() -> issuer.redeemRefreshToken(rotated.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
        assertThat(issuer.redeemRefreshToken(phone.refreshToken()).memberId()).isEqualTo(7);
        assertThat(issuer.redeemRefreshToken(others.refreshToken()).memberId()).isEqualTo(8);
        assertThat(issuer.redeemRefreshToken(issuer.issueWatch(7).refreshToken()).isWatch())
                .isTrue();
    }

    @Test
    @DisplayName("갱신 중에 폐기가 끼어도 회전한 새 토큰은 옛 세대를 이어 받아 거부된다")
    void revokeDuringRefresh_rotatedTokenIsStillRevoked() {
        RefreshGrant redeemed = issuer.redeemRefreshToken(issuer.issueWatch(7).refreshToken());

        issuer.revokeWatchSessions(7);
        TokenPair rotated = issuer.reissue(redeemed);

        assertThatThrownBy(() -> issuer.redeemRefreshToken(rotated.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    @DisplayName("폐기로 거부된 워치 토큰은 한 번 쓴 것으로 기록하지 않는다")
    void revokedWatchToken_isNotClaimed() {
        TokenPair watch = issuer.issueWatch(7);
        issuer.revokeWatchSessions(7);

        assertThatThrownBy(() -> issuer.redeemRefreshToken(watch.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
        assertThat(revocations.purgeExpired(T0.plus(JwtTokenIssuer.REFRESH_TTL))).isZero();
    }

    @Test
    @DisplayName("세대 저장소를 쓸 수 없으면 워치 발급·워치 갱신·일괄 폐기를 ProviderUnavailableException으로 거부하고 폰 갱신은 된다")
    void generationStoreDown_failsClosed() {
        TokenPair phone = issuer.issue(7);
        TokenPair watch = issuer.issueWatch(7);
        generations.unavailable();

        assertThatThrownBy(() -> issuer.issueWatch(7))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThatThrownBy(() -> issuer.redeemRefreshToken(watch.refreshToken()))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThatThrownBy(() -> issuer.revokeWatchSessions(7))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThat(issuer.redeemRefreshToken(phone.refreshToken()).isWatch()).isFalse();

        // 장애 동안 쓰지 못한 워치 토큰은 복구 뒤 그대로 쓸 수 있다
        generations.available();
        assertThat(issuer.redeemRefreshToken(watch.refreshToken()).isWatch()).isTrue();
    }

    @Test
    @DisplayName("W26: 워치 접근 토큰(회전한 것 포함)은 verifyGrant에서 워치용으로, 폰 접근 토큰은 폰용으로 읽힌다")
    void watchAccessToken_isMarked() {
        TokenPair watch = issuer.issueWatch(7);
        TokenPair rotated = issuer.reissue(issuer.redeemRefreshToken(watch.refreshToken()));

        assertThat(issuer.verifyGrant(watch.accessToken()).watch()).isTrue();
        assertThat(issuer.verifyGrant(rotated.accessToken()).watch()).isTrue();
        assertThat(issuer.verifyGrant(issuer.issue(7).accessToken()).watch()).isFalse();
        assertThat(issuer.verifyGrant(watch.accessToken()).memberId()).isEqualTo(7);
    }
}
