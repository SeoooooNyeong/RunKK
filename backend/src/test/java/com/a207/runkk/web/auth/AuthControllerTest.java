package com.a207.runkk.web.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.ProviderUnavailableException;
import com.a207.runkk.web.WebTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** POST /auth/login·logout·refresh, POST /members(설계 5.7.1, SQ-17)와 인증 필터. */
@DisplayName("web.auth 로그인·가입·토큰")
class AuthControllerTest extends WebTestSupport {

    private String login(String provider, String token) throws Exception {
        return write(Map.of("provider", provider, "providerToken", token));
    }

    @Test
    @DisplayName("새 계정은 가입 필요 → 가입 201 → 같은 계정 로그인은 토큰을 준다")
    void loginSignUpThenLogin() throws Exception {
        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(login("KAKAO", "same-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGN_UP_REQUIRED"))
                .andExpect(jsonPath("$.signUpTicket").isNotEmpty());
        Session s = signUp();
        mvc.perform(as(s, get("/api/v1/me/settings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordVisibility").value("PUBLIC"))
                .andExpect(jsonPath("$.consents.LOCATION").value(true));
    }

    @Test
    @DisplayName("갱신 토큰은 한 번만 쓰고 로그아웃하면 폐기된다")
    void refreshRotatesAndLogoutRevokes() throws Exception {
        Session s = signUp();
        String refreshed =
                mvc.perform(
                                post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(write(Map.of("refreshToken", s.refreshToken()))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.accessToken").isNotEmpty())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("refreshToken", s.refreshToken()))))
                .andExpect(status().isUnauthorized());

        String next = body(refreshed).get("refreshToken").asText();
        mvc.perform(
                        as(s, post("/api/v1/auth/logout"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("refreshToken", next))))
                .andExpect(status().isNoContent());
        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("refreshToken", next))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("제공자 무응답은 503, 인증 실패는 401과 사유 코드")
    void loginFailures() throws Exception {
        org.mockito.Mockito.doThrow(new ProviderUnavailableException("down"))
                .when(kakao)
                .authenticate(anyString());
        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(login("KAKAO", "t")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("로그인 제공자 연결 실패"));

        org.mockito.Mockito.doThrow(new AuthFailureException("bad"))
                .when(kakao)
                .authenticate(anyString());
        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(login("KAKAO", "t")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("AUTH_FAILED"));

        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"providerToken\":\"t\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(login("NAVER", "t")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("가입 거부: 위조 티켓, 필수 동의 누락(srs.md 6.3 1.2)")
    void signUpRejections() throws Exception {
        mvc.perform(
                        post("/api/v1/members")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(signUpBody("forged", "가짜곰"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIGN_UP_TICKET_INVALID"));

        String ticket =
                body(mvc.perform(
                                        post("/api/v1/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(login("KAKAO", "no-consent")))
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("signUpTicket")
                        .asText();
        mvc.perform(
                        post("/api/v1/members")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "signUpTicket",
                                                        ticket,
                                                        "consents",
                                                        Map.of(
                                                                "AGE_14_PLUS",
                                                                true,
                                                                "TERMS",
                                                                true)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_MISSING"))
                .andExpect(jsonPath("$.message").value("필수동의누락"));
    }

    @Test
    @DisplayName("토큰이 없거나 위조면 401, 관리툴 경로는 회원 필터를 지나지 않는다")
    void authFilter() throws Exception {
        mvc.perform(get("/api/v1/me/settings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/settings").header("Authorization", "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        Session s = signUp();
        mvc.perform(
                        get("/api/v1/me/settings")
                                .header("Authorization", "Bearer " + s.refreshToken()))
                .andExpect(status().isUnauthorized());
        // 회원 필터(401)가 아니라 web.admin의 OperatorCheckInterceptor가 관리툴 토큰을 거부한다(403, D-42·D-50)
        mvc.perform(get("/api/v1/admin/audits").header("Authorization", "Bearer forged"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mvc.perform(get("/api/v1/legal/TERMS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TERMS"));
    }
}
