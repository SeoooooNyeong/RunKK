package com.a207.runkk.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.GuestCredential;
import com.a207.runkk.member.ProviderUnavailableException;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * v0.11 게스트·연동·설정 엔드포인트(test.md 03 T-25 TC-6651·TC-6652·TC-6656·TC-6658~TC-6660, F.2.2): POST
 * /members/guest, POST /auth/guest/session, POST /me/social-account, GET·PATCH /me/settings(테마·근접
 * 알림).
 */
@DisplayName("web.auth 게스트 시작·세션 재개·소셜 연동과 설정")
class GuestAuthControllerTest extends WebTestSupport {

    private static final AtomicInteger CLIENT = new AtomicInteger();

    @Autowired JdbcTemplate jdbc;

    /** 공유 문맥의 게스트 생성 한도(접속지별 1시간 10회)에 걸리지 않게 요청마다 다른 접속지로 보낸다. */
    private static String nextClient() {
        int n = CLIENT.incrementAndGet();
        return "10.77." + (n / 250) + "." + (n % 250 + 1);
    }

    private MockHttpServletRequestBuilder guestStart(Map<String, Object> consents)
            throws Exception {
        return post("/api/v1/members/guest")
                .header("X-Real-IP", nextClient())
                .contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("consents", consents)));
    }

    private static Map<String, Object> required() {
        Map<String, Object> consents = new HashMap<>();
        consents.put("AGE_14_PLUS", true);
        consents.put("TERMS", true);
        consents.put("PRIVACY", true);
        consents.put("LOCATION", true);
        return consents;
    }

    private JsonNode startGuest() throws Exception {
        Map<String, Object> consents = required();
        consents.put("MARKETING", false);
        consents.put("LLM_TRANSFER", true);
        return body(
                mvc.perform(guestStart(consents))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private MockHttpServletRequestBuilder bearer(
            JsonNode guest, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + guest.get("accessToken").asText());
    }

    private MockHttpServletRequestBuilder resume(String credential) throws Exception {
        return post("/api/v1/auth/guest/session")
                .header("X-Real-IP", nextClient())
                .contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("guestCredential", credential)));
    }

    private MockHttpServletRequestBuilder link(JsonNode guest, String token) throws Exception {
        return bearer(guest, post("/api/v1/me/social-account"))
                .header("X-Real-IP", nextClient())
                .contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("provider", "KAKAO", "providerToken", token)));
    }

    @Test
    @DisplayName("TC-6651 게스트 시작 201: 러너 닉네임·자격 증명, GUEST 행·해시, 설정 조회 GUEST")
    void startAsGuest() throws Exception {
        JsonNode guest = startGuest();
        long memberId = guest.get("memberId").asLong();
        String credential = guest.get("guestCredential").asText();

        assertThat(guest.get("nickname").asText()).matches("러너\\d{4,5}");
        assertThat(credential).matches("g1\\." + memberId + "\\.[A-Za-z0-9_-]{43}");
        assertThat(guest.get("accessToken").asText()).isNotBlank();
        Map<String, Object> row =
                jdbc.queryForMap(
                        "select account_type, provider, provider_account_id, guest_credential_hash,"
                                + " record_visibility from member where id = ?",
                        memberId);
        assertThat(row.get("account_type")).isEqualTo("GUEST");
        assertThat(row.get("provider")).isNull();
        assertThat(row.get("provider_account_id")).isNull();
        assertThat(row.get("guest_credential_hash"))
                .isEqualTo(
                        GuestCredential.hashOf(
                                credential.substring(credential.lastIndexOf('.') + 1)));
        assertThat(row.get("record_visibility")).isEqualTo("PUBLIC");

        mvc.perform(bearer(guest, get("/api/v1/me/settings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("GUEST"))
                .andExpect(jsonPath("$.linkedProvider").doesNotExist())
                .andExpect(jsonPath("$.theme").value("SYSTEM"))
                .andExpect(jsonPath("$.battleSettings.proximityAlert").value(true))
                .andExpect(jsonPath("$.consents.LLM_TRANSFER").value(true))
                .andExpect(jsonPath("$.consents.MARKETING").value(false));
        mvc.perform(bearer(guest, get("/api/v1/members/" + memberId + "/profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").doesNotExist())
                .andExpect(jsonPath("$.tags").doesNotExist());
    }

    @Test
    @DisplayName("TC-6652 동의 거부 409, 받지 않는 동의·동의 없음 400")
    void startAsGuest_rejections() throws Exception {
        Map<String, Object> noLocation = required();
        noLocation.remove("LOCATION");
        mvc.perform(guestStart(noLocation))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_MISSING"));
        Map<String, Object> under = required();
        under.put("AGE_14_PLUS", false);
        mvc.perform(guestStart(under))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNDER_14"));
        Map<String, Object> heart = required();
        heart.put("HEART_RATE", true);
        mvc.perform(guestStart(heart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(
                        post("/api/v1/members/guest")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-6655·TC-6656 세션 재개: 정상 200, 위조·비밀 불일치 401 본문 없음")
    void resumeGuest() throws Exception {
        JsonNode guest = startGuest();
        String credential = guest.get("guestCredential").asText();

        JsonNode tokens =
                body(
                        mvc.perform(resume(credential))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(tokens.get("accessToken").asText()).isNotBlank();
        assertThat(tokens.get("refreshToken").asText()).isNotBlank();

        String last = credential.substring(credential.length() - 1);
        String flipped =
                credential.substring(0, credential.length() - 1) + ("A".equals(last) ? "B" : "A");
        for (String bad : new String[] {"g1.x.y", flipped, "g1.999999." + "A".repeat(43)}) {
            mvc.perform(resume(bad))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(""));
        }
        mvc.perform(
                        post("/api/v1/auth/guest/session")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("guestCredential", "x".repeat(201)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-6658·TC-6660 연동: LINKED·KAKAO, 토큰 그대로, 옛 자격 증명 401, 같은 계정 로그인은 같은 회원")
    void linkSocialAccount() throws Exception {
        JsonNode guest = startGuest();
        long memberId = guest.get("memberId").asLong();
        String kakao = "k-new-" + UUID.randomUUID();

        mvc.perform(link(guest, kakao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("LINKED"))
                .andExpect(jsonPath("$.provider").value("KAKAO"));
        mvc.perform(bearer(guest, get("/api/v1/me/settings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("LINKED"))
                .andExpect(jsonPath("$.linkedProvider").value("KAKAO"));
        assertThat(
                        jdbc.queryForObject(
                                "select guest_credential_hash from member where id = ?",
                                String.class,
                                memberId))
                .isNull();

        mvc.perform(resume(guest.get("guestCredential").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(Map.of("provider", "KAKAO", "providerToken", kakao))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGGED_IN"));
        mvc.perform(link(guest, "k-other-" + UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_LINKED"));
    }

    @Test
    @DisplayName("TC-6659 연동 거부: ALREADY_MEMBER 409, 인증 실패 401, 무응답 503, 게스트 유지")
    void linkSocialAccount_rejections() throws Exception {
        Session linked = signUp();
        String taken =
                jdbc.queryForObject(
                        "select provider_account_id from member where id = ?",
                        String.class,
                        linked.memberId());
        JsonNode guest = startGuest();

        mvc.perform(link(guest, taken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"))
                .andExpect(jsonPath("$.message").value("이미 가입된 계정"));
        doThrow(new AuthFailureException("bad")).when(kakao).authenticate(eq("bad-token"));
        mvc.perform(link(guest, "bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"));
        doThrow(new ProviderUnavailableException("down")).when(kakao).authenticate(eq("slow"));
        mvc.perform(link(guest, "slow"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"));
        mvc.perform(bearer(guest, get("/api/v1/me/settings")))
                .andExpect(jsonPath("$.accountType").value("GUEST"));
        mvc.perform(
                        post("/api/v1/me/social-account")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("provider", "KAKAO", "providerToken", "t"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-6629 설정: THEME 저장·조회, BATTLE_SETTINGS proximityAlert는 빠지면 유지")
    void themeAndProximityAlert() throws Exception {
        Session s = signUp();
        mvc.perform(
                        as(s, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("item", "THEME", "value", "DARK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.theme").value("DARK"));
        mvc.perform(
                        as(s, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("item", "THEME", "value", "BLUE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTING_INVALID"));
        Map<String, Object> off =
                Map.of(
                        "defaultDistance", "KM5",
                        "voice", true,
                        "vibration", true,
                        "gapAlertInterval", "KM1",
                        "proximityAlert", false);
        mvc.perform(
                        as(s, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("item", "BATTLE_SETTINGS", "value", off))))
                .andExpect(status().isOk());
        Map<String, Object> oldApp =
                Map.of(
                        "defaultDistance",
                        "KM3",
                        "voice",
                        false,
                        "vibration",
                        true,
                        "gapAlertInterval",
                        "KM1");
        mvc.perform(
                        as(s, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("item", "BATTLE_SETTINGS", "value", oldApp))))
                .andExpect(status().isOk());
        mvc.perform(as(s, get("/api/v1/me/settings")))
                .andExpect(jsonPath("$.theme").value("DARK"))
                .andExpect(jsonPath("$.accountType").value("LINKED"))
                .andExpect(jsonPath("$.linkedProvider").value("KAKAO"))
                .andExpect(jsonPath("$.battleSettings.defaultDistance").value("KM3"))
                .andExpect(jsonPath("$.battleSettings.proximityAlert").value(false));
    }
}
