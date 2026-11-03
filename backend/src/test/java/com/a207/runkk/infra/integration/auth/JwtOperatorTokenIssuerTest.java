package com.a207.runkk.infra.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.SocialAccount;
import com.a207.runkk.operator.OperatorId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 관리툴 토큰(D-42): 회원 토큰과 서명 키·발급자를 나눠 서로의 검사를 통과하지 못한다. 만료 8시간. */
@DisplayName("JwtOperatorTokenIssuer")
class JwtOperatorTokenIssuerTest {

    private static final String ADMIN_SECRET = "admin-secret-for-tests-0123456789abcdef";
    private static final String MEMBER_SECRET = "member-secret-for-tests-0123456789abcdef";
    private static final Instant T0 = Instant.parse("2026-10-21T00:00:00Z");
    private static final OperatorId OPERATOR =
            new OperatorId(new SocialAccount(ProviderType.KAKAO, "op-1"));

    private static JwtOperatorTokenIssuer issuer(Instant at) {
        return new JwtOperatorTokenIssuer(
                ADMIN_SECRET, MEMBER_SECRET, Clock.fixed(at, ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("발급한 토큰을 검증하면 같은 운영자(actorId KAKAO:op-1)")
    void roundTrip() {
        String token = issuer(T0).issue(OPERATOR);
        OperatorId verified = issuer(T0.plusSeconds(60)).verify(token);
        assertThat(verified).isEqualTo(OPERATOR);
        assertThat(verified.actorId()).isEqualTo("KAKAO:op-1");
    }

    @Test
    @DisplayName("만료(8시간 뒤)·위조(다른 키)·형식 오류·빈 값은 PermissionDeniedException")
    void rejectsExpiredForgedMalformed() {
        String token = issuer(T0).issue(OPERATOR);
        assertThatThrownBy(() -> issuer(T0.plus(JwtOperatorTokenIssuer.TTL)).verify(token))
                .isInstanceOf(PermissionDeniedException.class);
        JwtOperatorTokenIssuer other =
                new JwtOperatorTokenIssuer(
                        "another-admin-secret-0123456789abcdefgh",
                        MEMBER_SECRET,
                        Clock.systemUTC());
        assertThatThrownBy(() -> other.verify(token)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> issuer(T0).verify("not-a-jwt"))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> issuer(T0).verify(" "))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("회원 접근 토큰은 관리툴 검사를, 관리툴 토큰은 회원 검사를 통과하지 못한다")
    void memberAndAdminTokensDoNotCross() {
        JwtTokenIssuer members =
                new JwtTokenIssuer(
                        MEMBER_SECRET,
                        Clock.fixed(T0, ZoneId.of("UTC")),
                        new InMemoryRefreshTokenRevocations(),
                        new InMemoryWatchSessionGenerations());
        String memberToken = members.issue(7).accessToken();
        assertThatThrownBy(() -> issuer(T0).verify(memberToken))
                .isInstanceOf(PermissionDeniedException.class);
        String adminToken = issuer(T0).issue(OPERATOR);
        assertThatThrownBy(() -> members.verify(adminToken))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    @DisplayName("관리툴 키가 짧거나 회원 키와 같으면 기동을 멈춘다, 비면 임의 키")
    void secretRules() {
        Clock clock = Clock.systemUTC();
        assertThatThrownBy(() -> new JwtOperatorTokenIssuer("short", MEMBER_SECRET, clock))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtOperatorTokenIssuer(MEMBER_SECRET, MEMBER_SECRET, clock))
                .isInstanceOf(IllegalStateException.class);
        JwtOperatorTokenIssuer random = new JwtOperatorTokenIssuer("", MEMBER_SECRET, clock);
        assertThat(random.verify(random.issue(OPERATOR))).isEqualTo(OPERATOR);
    }
}
