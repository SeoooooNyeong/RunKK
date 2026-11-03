package com.a207.runkk.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.web.WebTestSupport;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 코드 리뷰 2차 M5: 공개 인증 경로(로그인·갱신·가입)의 시도 제한. 접속지(nginx가 넣는 X-Real-IP는 프록시에서 온 요청만 믿는다)별 요청 수와 실패 수,
 * 자격(제공자 토큰·갱신 토큰·가입 티켓)별 실패 수를 넘으면 429 TOO_MANY_ATTEMPTS다.
 */
@TestPropertySource(
        properties = {
            "runkk.auth.attempts.client-requests=5",
            "runkk.auth.attempts.request-window=1m",
            "runkk.auth.attempts.client-failures=3",
            "runkk.auth.attempts.credential-failures=2",
            "runkk.auth.attempts.failure-window=10m",
            "runkk.auth.attempts.guest-starts=3",
            "runkk.auth.attempts.guest-start-window=1h",
            "runkk.auth.attempts.watch-sessions=3",
            "runkk.auth.attempts.watch-session-window=1h"
        })
@DisplayName("web.auth 공개 인증 경로 시도 제한(M5)")
class AuthAttemptLimitTest extends WebTestSupport {

    private static final String PROXY = "172.18.0.5";

    private static MockHttpServletRequestBuilder from(
            MockHttpServletRequestBuilder request, String clientIp) {
        return request.with(
                r -> {
                    r.setRemoteAddr(PROXY);
                    r.addHeader("X-Real-IP", clientIp);
                    return r;
                });
    }

    private MockHttpServletRequestBuilder login(String token) throws Exception {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("provider", "KAKAO", "providerToken", token)));
    }

    private MockHttpServletRequestBuilder refresh(String token) throws Exception {
        return post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("refreshToken", token)));
    }

    @Test
    @DisplayName("같은 접속지의 요청이 창 안 상한을 넘으면 429, 다른 접속지는 영향 없음")
    void clientRequestLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(from(login("user-" + UUID.randomUUID()), "203.0.113.10"))
                    .andExpect(status().isOk());
        }
        mvc.perform(from(login("user-" + UUID.randomUUID()), "203.0.113.10"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        mvc.perform(from(login("user-" + UUID.randomUUID()), "203.0.113.11"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("밖에서 바로 온 요청은 X-Real-IP를 바꿔도 같은 접속지로 센다")
    void spoofedRealIp_isIgnored() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(
                            login("user-" + UUID.randomUUID())
                                    .with(
                                            r -> {
                                                r.setRemoteAddr("198.51.100.20");
                                                r.addHeader(
                                                        "X-Real-IP",
                                                        "10.9.9." + r.hashCode() % 200);
                                                return r;
                                            }))
                    .andExpect(status().isOk());
        }
        mvc.perform(
                        login("user-" + UUID.randomUUID())
                                .with(
                                        r -> {
                                            r.setRemoteAddr("198.51.100.20");
                                            r.addHeader("X-Real-IP", "10.9.9.250");
                                            return r;
                                        }))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("갱신 실패(401)가 접속지 상한에 닿으면 이후 갱신은 429")
    void clientFailureLimit_onRefresh() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(from(refresh("not-a-token-" + i), "203.0.113.20"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(from(refresh("not-a-token-x"), "203.0.113.20"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("같은 가입 티켓의 실패가 자격 상한에 닿으면 접속지를 바꿔도 429")
    void credentialFailureLimit_onSignUp() throws Exception {
        String ticket = "forged-ticket";
        for (String ip : new String[] {"203.0.113.30", "203.0.113.31"}) {
            mvc.perform(
                            from(
                                    post("/api/v1/members")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(write(signUpBody(ticket, "제한러너"))),
                                    ip))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SIGN_UP_TICKET_INVALID"));
        }
        mvc.perform(
                        from(
                                post("/api/v1/members")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(write(signUpBody(ticket, "제한러너"))),
                                "203.0.113.32"))
                .andExpect(status().isTooManyRequests());
    }

    private MockHttpServletRequestBuilder guestStart(boolean location) throws Exception {
        return post("/api/v1/members/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        write(
                                Map.of(
                                        "consents",
                                        Map.of(
                                                "AGE_14_PLUS", true,
                                                "TERMS", true,
                                                "PRIVACY", true,
                                                "LOCATION", location))));
    }

    @Test
    @DisplayName("TC-6654 게스트 생성 한도: 같은 접속지의 생성 성공이 상한에 닿으면 429·회원 0, 다른 접속지는 영향 없음")
    void guestStartLimit() throws Exception {
        mvc.perform(from(guestStart(false), "203.0.113.40")).andExpect(status().isConflict());
        for (int i = 0; i < 3; i++) {
            mvc.perform(from(guestStart(true), "203.0.113.40")).andExpect(status().isCreated());
        }
        mvc.perform(from(guestStart(true), "203.0.113.40"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        mvc.perform(from(guestStart(true), "203.0.113.41")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("게스트 세션 재개도 자격 증명별 실패가 상한에 닿으면 접속지를 바꿔도 429")
    void credentialFailureLimit_onGuestSession() throws Exception {
        String forged = "g1.1." + "A".repeat(43);
        for (String ip : new String[] {"203.0.113.50", "203.0.113.51"}) {
            mvc.perform(
                            from(
                                    post("/api/v1/auth/guest/session")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(write(Map.of("guestCredential", forged))),
                                    ip))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(
                        from(
                                post("/api/v1/auth/guest/session")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(write(Map.of("guestCredential", forged))),
                                "203.0.113.52"))
                .andExpect(status().isTooManyRequests());
    }

    private String guestBearer(String clientIp) throws Exception {
        String guest =
                mvc.perform(from(guestStart(true), clientIp))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return "Bearer " + body(guest).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder watchSession(String bearer, String clientIp) {
        return from(post("/api/v1/auth/watch-session").header("Authorization", bearer), clientIp);
    }

    @Test
    @DisplayName("워치 세션 발급(D-372): 회원별 발급 한도는 접속지를 바꿔도 429, 접속지 요청 상한도 함께 센다")
    void watchSessionLimits() throws Exception {
        String first = guestBearer("203.0.113.60");
        String second = guestBearer("203.0.113.60");
        for (int i = 0; i < 3; i++) {
            mvc.perform(watchSession(first, "203.0.113.61")).andExpect(status().isOk());
        }
        mvc.perform(watchSession(first, "203.0.113.62"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));

        // 다른 회원은 회원 한도와 무관하지만 같은 접속지 요청 상한(5)은 함께 센다
        mvc.perform(watchSession(second, "203.0.113.61")).andExpect(status().isOk());
        mvc.perform(watchSession(second, "203.0.113.61")).andExpect(status().isOk());
        mvc.perform(watchSession(second, "203.0.113.61")).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("BE12-7: 설정이 없을 때 워치 세션 발급 한도 기본값은 1시간 20회다(I14-5)")
    void defaultWatchSessionLimit_isTwentyPerHour() {
        AuthAttemptGuard.Limits defaults =
                new Binder(new MapConfigurationPropertySource(Map.of()))
                        .bindOrCreate("runkk.auth.attempts", AuthAttemptGuard.Limits.class);

        assertThat(defaults.watchSessions()).isEqualTo(20);
        assertThat(defaults.watchSessionWindow()).isEqualTo(Duration.ofHours(1));
    }
}
