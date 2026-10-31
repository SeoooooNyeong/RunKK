package com.a207.runkk.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** POST /auth/watch-session(D-372, FR-240, SQ-43): 워치용 토큰 쌍. 폰 토큰은 그대로 유효하다. */
@DisplayName("web.auth POST /auth/watch-session")
class WatchSessionControllerTest extends WebTestSupport {

    private static final String PATH = "/api/v1/auth/watch-session";
    private static final AtomicInteger CLIENT = new AtomicInteger();

    /** 공유 문맥의 접속지별 요청 한도에 걸리지 않게 시험마다 다른 접속지로 보낸다. */
    private static String nextClient() {
        int n = CLIENT.incrementAndGet();
        return "10.79." + (n / 250) + "." + (n % 250 + 1);
    }

    private MockHttpServletRequestBuilder watchSession(String accessToken, String client) {
        return post(PATH)
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Real-IP", client);
    }

    private JsonNode issue(String accessToken) throws Exception {
        return body(
                mvc.perform(watchSession(accessToken, nextClient()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private MockHttpServletRequestBuilder refresh(String refreshToken) throws Exception {
        return post("/api/v1/auth/refresh")
                .header("X-Real-IP", nextClient())
                .contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("refreshToken", refreshToken)));
    }

    @Test
    @DisplayName("200 TokenResponse: 새 토큰 쌍으로 API를 쓰고 갱신하며, 폰 토큰 쌍도 그대로 쓸 수 있다")
    void issuesSeparatePair_phoneStaysValid() throws Exception {
        Session phone = signUp();

        JsonNode watch = issue(phone.accessToken());

        assertThat(watch.fieldNames()).toIterable().containsExactly("accessToken", "refreshToken");
        String watchRefresh = watch.get("refreshToken").asText();
        assertThat(watchRefresh).isNotBlank().isNotEqualTo(phone.refreshToken());
        mvc.perform(
                        get("/api/v1/me/settings")
                                .header(
                                        "Authorization",
                                        "Bearer " + watch.get("accessToken").asText()))
                .andExpect(status().isOk());
        mvc.perform(refresh(watchRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        // 폰 쌍은 무효가 되지 않는다(갱신 토큰 회전은 쌍마다 따로)
        mvc.perform(as(phone, get("/api/v1/me/settings"))).andExpect(status().isOk());
        mvc.perform(refresh(phone.refreshToken())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("게스트도 받는다")
    void guest_isIssued() throws Exception {
        JsonNode guest =
                body(
                        mvc.perform(
                                        post("/api/v1/members/guest")
                                                .header("X-Real-IP", nextClient())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        write(
                                                                Map.of(
                                                                        "consents",
                                                                        Map.of(
                                                                                "AGE_14_PLUS",
                                                                                true,
                                                                                "TERMS",
                                                                                true,
                                                                                "PRIVACY",
                                                                                true,
                                                                                "LOCATION",
                                                                                true)))))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());

        JsonNode watch = issue(guest.get("accessToken").asText());

        assertThat(watch.get("refreshToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("401: 토큰 없음·위조 토큰·갱신 토큰을 접근 토큰으로 씀·탈퇴 회원")
    void unauthorized() throws Exception {
        mvc.perform(post(PATH).header("X-Real-IP", nextClient()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(watchSession("forged", nextClient())).andExpect(status().isUnauthorized());
        Session s = signUp();
        mvc.perform(watchSession(s.refreshToken(), nextClient()))
                .andExpect(status().isUnauthorized());

        mvc.perform(as(s, delete("/api/v1/me"))).andExpect(status().isOk());
        mvc.perform(watchSession(s.accessToken(), nextClient()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
