package com.a207.runkk.infra.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.member.AccessGrant;
import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.ProviderUnavailableException;
import com.a207.runkk.member.SocialAccount;
import com.a207.runkk.member.TokenPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** JwtTokenIssuer(D-19): 종류별 검증, 만료, 갱신 토큰 회전·폐기, 서명 키 규칙. */
@org.junit.jupiter.api.DisplayName("회원 JWT 발급·검증")
class JwtTokenIssuerTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-32";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant T0 = Instant.parse("2026-10-20T06:00:00Z");

    /**
     * 서명 가운데 글자 하나를 다른 글자로 바꾼다. 끝 두 글자를 고정 문자열로 바꾸면 원래 끝 글자가 같거나 마지막 글자의 버려지는 비트만 바뀌어 서명이 그대로일 수
     * 있다(드물게 시험이 흔들림).
     */
    private static String tamperSignature(String token) {
        int i = token.length() - 10;
        char replaced = token.charAt(i) == 'A' ? 'B' : 'A';
        return token.substring(0, i) + replaced + token.substring(i + 1);
    }

    private static JwtTokenIssuer at(Instant now) {
        return new JwtTokenIssuer(
                SECRET, Clock.fixed(now, SEOUL), new InMemoryRefreshTokenRevocations());
    }

    @Test
    void accessToken_verifiesToMemberId_untilOneHour() {
        TokenPair pair = at(T0).issue(42);
        assertThat(at(T0).verify(pair.accessToken())).isEqualTo(42);
        assertThat(at(T0.plusSeconds(3599)).verify(pair.accessToken())).isEqualTo(42);
        assertThatThrownBy(() -> at(T0.plusSeconds(3600)).verify(pair.accessToken()))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6192 verifyGrant: 59:59는 AccessGrant(회원, 발급 + 1시간), 1:00:00·1:00:01은"
                    + " AuthFailureException")
    void verifyGrant_expiryBoundary() {
        TokenPair pair = at(T0).issue(42);
        assertThat(at(T0.plusSeconds(3599)).verifyGrant(pair.accessToken()))
                .isEqualTo(new AccessGrant(42, T0.plusSeconds(3600)));
        assertThatThrownBy(() -> at(T0.plusSeconds(3600)).verifyGrant(pair.accessToken()))
                .isInstanceOf(AuthFailureException.class);
        assertThatThrownBy(() -> at(T0.plusSeconds(3601)).verifyGrant(pair.accessToken()))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6193 verifyGrant: 서명 위조·관리툴 토큰·갱신 토큰은 AuthFailureException")
    void verifyGrant_rejectsForgedOperatorAndRefresh() {
        TokenPair pair = at(T0).issue(42);
        String forged = tamperSignature(pair.accessToken());
        String operator =
                new JwtOperatorTokenIssuer(
                                "admin-secret-for-tests-0123456789abcdef",
                                SECRET,
                                Clock.fixed(T0, SEOUL))
                        .issue(
                                new com.a207.runkk.operator.OperatorId(
                                        new SocialAccount(ProviderType.KAKAO, "op-1")));
        for (String token : java.util.List.of(forged, operator, pair.refreshToken())) {
            assertThatThrownBy(() -> at(T0).verifyGrant(token))
                    .isInstanceOf(AuthFailureException.class);
        }
    }

    @Test
    void tokenKinds_cannotBeSwapped() {
        JwtTokenIssuer issuer = at(T0);
        TokenPair pair = issuer.issue(42);
        String ticket = issuer.issueSignUpTicket(new SocialAccount(ProviderType.KAKAO, "1"));

        assertThatThrownBy(() -> issuer.verify(pair.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
        assertThatThrownBy(() -> issuer.verify(ticket)).isInstanceOf(AuthFailureException.class);
        assertThatThrownBy(() -> issuer.redeemRefreshToken(pair.accessToken()))
                .isInstanceOf(AuthFailureException.class);
        assertThatThrownBy(() -> issuer.redeemSignUpTicket(pair.accessToken()))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    void signUpTicket_roundTripsForFifteenMinutes() {
        SocialAccount account = new SocialAccount(ProviderType.GOOGLE, "sub-1");
        String ticket = at(T0).issueSignUpTicket(account);
        assertThat(at(T0.plusSeconds(899)).redeemSignUpTicket(ticket)).isEqualTo(account);
        assertThatThrownBy(() -> at(T0.plusSeconds(900)).redeemSignUpTicket(ticket))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    void refreshToken_isSingleUse_andRevocable() {
        JwtTokenIssuer issuer = at(T0);
        TokenPair pair = issuer.issue(7);
        assertThat(issuer.redeemRefreshToken(pair.refreshToken())).isEqualTo(7);
        assertThatThrownBy(() -> issuer.redeemRefreshToken(pair.refreshToken()))
                .isInstanceOf(AuthFailureException.class);

        TokenPair other = issuer.issue(7);
        issuer.revoke(other.refreshToken());
        assertThatThrownBy(() -> issuer.redeemRefreshToken(other.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
        issuer.revoke("not-a-token");
        assertThatThrownBy(
                        () ->
                                at(T0.plusSeconds(14 * 24 * 3600))
                                        .redeemRefreshToken(issuer.issue(7).refreshToken()))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "M6 후속: 폐기 목록을 쓸 수 없으면 갱신·로그아웃을 거부한다(fail-closed), 토큰은 쓰지 않은 채로 남는다")
    void revocationStoreDown_failsClosed() {
        InMemoryRefreshTokenRevocations store = new InMemoryRefreshTokenRevocations();
        TokenPair pair = at(T0).issue(7);
        JwtTokenIssuer down =
                new JwtTokenIssuer(SECRET, Clock.fixed(T0, SEOUL), store.unavailable());
        assertThatThrownBy(() -> down.redeemRefreshToken(pair.refreshToken()))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThatThrownBy(() -> down.revoke(pair.refreshToken()))
                .isInstanceOf(ProviderUnavailableException.class);
        down.revoke("not-a-token"); // 읽을 수 없는 토큰은 저장소를 부르지 않는다

        JwtTokenIssuer recovered = at(T0);
        assertThat(recovered.redeemRefreshToken(pair.refreshToken())).isEqualTo(7);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("M6 후속: 만료 정리는 서비스 시각(한국) now까지 만료된 폐기 행만 지운다")
    void purgeExpiredRevocations_removesOnlyExpiredRows() {
        InMemoryRefreshTokenRevocations store = new InMemoryRefreshTokenRevocations();
        JwtTokenIssuer issuer = new JwtTokenIssuer(SECRET, Clock.fixed(T0, SEOUL), store);
        issuer.revoke(issuer.issue(1).refreshToken());
        JwtTokenIssuer later =
                new JwtTokenIssuer(SECRET, Clock.fixed(T0.plusSeconds(3600), SEOUL), store);
        String young = later.issue(2).refreshToken();
        later.revoke(young);

        java.time.LocalDateTime expiryOfFirst =
                java.time.LocalDateTime.ofInstant(T0.plus(JwtTokenIssuer.REFRESH_TTL), SEOUL);
        assertThat(issuer.purgeExpiredRevocations(expiryOfFirst.minusSeconds(1))).isZero();
        assertThat(issuer.purgeExpiredRevocations(expiryOfFirst)).isEqualTo(1);
        assertThatThrownBy(() -> later.redeemRefreshToken(young))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    void forgedOrForeignKeyTokens_areRejected() {
        String token = at(T0).issue(1).accessToken();
        JwtTokenIssuer otherKey =
                new JwtTokenIssuer(
                        "another-secret-another-secret-12345",
                        Clock.fixed(T0, SEOUL),
                        new InMemoryRefreshTokenRevocations());
        assertThatThrownBy(() -> otherKey.verify(token)).isInstanceOf(AuthFailureException.class);
        assertThatThrownBy(() -> at(T0).verify(tamperSignature(token)))
                .isInstanceOf(AuthFailureException.class);
        assertThatThrownBy(() -> at(T0).verify(null)).isInstanceOf(AuthFailureException.class);
        assertThatThrownBy(() -> at(T0).verify("abc")).isInstanceOf(AuthFailureException.class);
    }

    @Test
    void secretRules() {
        assertThatThrownBy(
                        () ->
                                new JwtTokenIssuer(
                                        "short",
                                        Clock.systemUTC(),
                                        new InMemoryRefreshTokenRevocations()))
                .isInstanceOf(IllegalStateException.class);
        JwtTokenIssuer random =
                new JwtTokenIssuer(
                        "", Clock.fixed(T0, SEOUL), new InMemoryRefreshTokenRevocations());
        assertThat(random.verify(random.issue(3).accessToken())).isEqualTo(3);
    }
}
