package com.a207.runkk.web.me;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.common.Tier;
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
    @DisplayName(
            "TC-5797 GET /me/placement: 새 회원은 IN_PROGRESS 0/3(다음 판 1, 임시 티어 없음, 티어 UNRANKED, 판 없음),"
                    + " 배치고사 없이 티어가 있으면 EXEMPT, 인증 없으면 401")
    void placement_returnsOwnProgress() throws Exception {
        Session s = signUp();
        mvc.perform(as(s, get("/api/v1/me/placement")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.countedRounds").value(0))
                .andExpect(jsonPath("$.nextRound").value(1))
                .andExpect(jsonPath("$.provisionalTier").value(nullValue()))
                .andExpect(jsonPath("$.tier").value("UNRANKED"))
                .andExpect(jsonPath("$.rounds").isEmpty());

        rank(s, Tier.SILVER);
        mvc.perform(as(s, get("/api/v1/me/placement")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXEMPT"))
                .andExpect(jsonPath("$.countedRounds").value(0))
                .andExpect(jsonPath("$.nextRound").value(nullValue()))
                .andExpect(jsonPath("$.tier").value("SILVER"));
        mvc.perform(get("/api/v1/me/placement")).andExpect(status().isUnauthorized());
    }

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
    @DisplayName("프로필: 닉네임·소개를 바꾸고(태그는 보내도 무시, D-91), 소개 41자는 거부한다")
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
                .andExpect(jsonPath("$.tags").doesNotExist());
        mvc.perform(
                        as(s, patch("/api/v1/me/profile"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("intro", "가".repeat(41)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTRO_TOO_LONG"));
        Session other = signUp();
        mvc.perform(
                        as(other, patch("/api/v1/me/profile"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("nickname", "새이름" + s.memberId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));
        mvc.perform(
                        as(other, patch("/api/v1/me/profile"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("nickname", "가"))))
                .andExpect(jsonPath("$.code").value("NICKNAME_INVALID"));
    }

    @Test
    @DisplayName("탈퇴 거부는 409 공통 오류 형식(code·message): 크루장 양도 필요")
    void withdraw_rejectedByGuard() throws Exception {
        Session leader = signUp();
        Session member = signUp();
        String created =
                mvc.perform(
                                as(leader, post("/api/v1/crews"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                write(
                                                        Map.of(
                                                                "name",
                                                                "탈퇴시험" + leader.memberId(),
                                                                "tags",
                                                                java.util.List.of("SOCIAL_RUN"),
                                                                "visibility",
                                                                "PUBLIC",
                                                                "joinPolicy",
                                                                "FREE"))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long crewId = body(created).get("id").asLong();
        mvc.perform(
                        as(member, post("/api/v1/crews/join"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("crewId", crewId))))
                .andExpect(jsonPath("$.status").value("JOINED"));
        mvc.perform(as(leader, delete("/api/v1/me")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEADER_MUST_TRANSFER"))
                .andExpect(jsonPath("$.message").value("크루장 양도 필요"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    @Test
    @DisplayName("탈퇴: 첫 요청은 완료, 그 뒤 남은 접근 토큰은 401(L21: 탈퇴 회원 토큰을 1시간 동안 받지 않는다)")
    void withdraw() throws Exception {
        Session s = signUp();
        mvc.perform(as(s, delete("/api/v1/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawn").value(true));
        mvc.perform(as(s, delete("/api/v1/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(as(s, get("/api/v1/me/disposals"))).andExpect(status().isUnauthorized());
    }
}
