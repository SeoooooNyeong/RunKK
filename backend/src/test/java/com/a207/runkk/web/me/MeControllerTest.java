package com.a207.runkk.web.me;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.web.WebTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * PATCH /me/profile, GET·PATCH /me/settings, PUT·DELETE /me/consents/{item}, GET /legal/{type},
 * DELETE /me.
 */
@DisplayName("web.me 본인 계정·설정·동의·탈퇴")
class MeControllerTest extends WebTestSupport {

    @Test
    @DisplayName("설정: 단위·러닝설정을 바꾸고, 워치 연동은 심박 동의를 먼저 요구하며, 동의 항목은 거부한다")
    void settings() throws Exception {
        Session s = signUp();
        mvc.perform(
                        as(s, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("item", "UNIT", "value", "MI"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome.applied").value(true))
                .andExpect(jsonPath("$.settings.unit").value("MI"));
        mvc.perform(
                        as(s, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "item",
                                                        "RUN_SETTINGS",
                                                        "value",
                                                        Map.of(
                                                                "target", "KM5",
                                                                "voice", false,
                                                                "vibration", true,
                                                                "followCoachPlan", true)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.runSettings.target").value("KM5"));
        mvc.perform(
                        as(s, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("item", "WATCH_LINK", "value", "true"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome.applied").value(false))
                .andExpect(jsonPath("$.outcome.requiredConsent").value("HEART_RATE"));
        mvc.perform(
                        as(s, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "item",
                                                        "LOCATION_CONSENT",
                                                        "value",
                                                        "false"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_ITEM_NOT_SETTING"));
        mvc.perform(
                        as(s, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("item", "UNIT", "value", "LY"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTING_INVALID"));
        mvc.perform(
                        as(s, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "item",
                                                        "ALERT",
                                                        "value",
                                                        "false",
                                                        "notificationType",
                                                        "KICKED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.notificationPrefs.KICKED").value(false));
    }

    @Test
    @DisplayName("동의: 심박 동의 뒤 워치 연동을 켜고, 위치 철회는 파기 지시를 만들며, 필수 동의 철회는 400")
    void consents() throws Exception {
        Session s = signUp();
        mvc.perform(as(s, put("/api/v1/me/consents/HEART_RATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.before").value(false))
                .andExpect(jsonPath("$.after").value(true));
        mvc.perform(
                        as(s, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("item", "WATCH_LINK", "value", "true"))))
                .andExpect(jsonPath("$.settings.watchLinked").value(true));
        mvc.perform(as(s, delete("/api/v1/me/consents/LOCATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.after").value(false));
        mvc.perform(as(s, get("/api/v1/me/disposals")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("ROUTE"))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"));
        mvc.perform(as(s, delete("/api/v1/me/consents/TERMS"))).andExpect(status().isBadRequest());
        mvc.perform(as(s, delete("/api/v1/me/consents/UNKNOWN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("프로필: 닉네임·소개·태그를 바꾸고, 소개 41자는 거부한다")
    void profile() throws Exception {
        Session s = signUp();
        mvc.perform(
                        as(s, patch("/api/v1/me/profile"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "nickname",
                                                        "새이름" + s.memberId(),
                                                        "intro",
                                                        "반가워요",
                                                        "tags",
                                                        java.util.List.of("DAWN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새이름" + s.memberId()))
                .andExpect(jsonPath("$.tags[0]").value("DAWN"));
        mvc.perform(
                        as(s, patch("/api/v1/me/profile"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("intro", "가".repeat(41)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTRO_TOO_LONG"));
    }

    @Test
    @DisplayName("탈퇴: 첫 요청은 완료, 두 번째는 처리할 수 없는 상태(409)")
    void withdraw() throws Exception {
        Session s = signUp();
        mvc.perform(as(s, delete("/api/v1/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawn").value(true));
        mvc.perform(as(s, delete("/api/v1/me")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mvc.perform(as(s, get("/api/v1/me/disposals")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
