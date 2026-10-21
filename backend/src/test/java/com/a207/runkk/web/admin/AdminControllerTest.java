package com.a207.runkk.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.SocialAccount;
import com.a207.runkk.operator.OperatorId;
import com.a207.runkk.operator.OperatorTokenIssuer;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * T-13 관리툴(D-42, D-50): 관리툴 토큰 없음·위조·화이트리스트 밖이면 세 관리 요청 모두 403, 회원 JWT로 /admin/* 거부, 확인된 운영자
 * 계정(actorId)이 감사 기록에 남는다. T-11 일부: LLM_DAILY_LIMIT 전체 201 이상 거부(D-62), SHOP_ITEMS 기존 아이템 삭제
 * 거부(D-44). 상점 시험이 쓰는 기존 ID를 지우지 않는다(H2 문맥 공유).
 */
@DisplayName("web.admin 관리툴(T-13, T-11 일부)")
class AdminControllerTest extends WebTestSupport {

    private static final String OPERATOR = "admin-op";
    private static final String ACTOR = "KAKAO:" + OPERATOR;

    @Autowired OperationSettingService settings;
    @Autowired OperatorTokenIssuer operatorTokens;
    @Autowired com.a207.runkk.policy.OperationPolicy policy;

    @BeforeEach
    void whitelist() {
        settings.change(
                "test-setup",
                SettingKey.OPERATOR_WHITELIST,
                "{\"accounts\":{\"KAKAO\":[\"" + OPERATOR + "\"]}}");
    }

    private String login(String providerToken) throws Exception {
        JsonNode session =
                body(
                        mvc.perform(
                                        post("/api/v1/admin/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        write(
                                                                Map.of(
                                                                        "provider",
                                                                        "KAKAO",
                                                                        "providerToken",
                                                                        providerToken))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.actorId").value(ACTOR))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        return session.get("adminToken").asText();
    }

    private Map<String, Object> celebrity() {
        return Map.of(
                "anonymousName",
                "마라토너 K",
                "distanceMeters",
                10_000,
                "finishSeconds",
                1_800,
                "splits",
                List.of(Map.of("elapsedSeconds", 900, "distanceMeters", 5_000)),
                "sourceName",
                "공식 대회 기록",
                "sourceUrl",
                "https://example.com/result");
    }

    /** 세 관리 요청(유명인 등록, 기준값 변경, 감사 조회). */
    private List<MockHttpServletRequestBuilder> threeAdminRequests() throws Exception {
        LocalDate today = LocalDate.now();
        return List.of(
                post("/api/v1/admin/celebrities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(write(celebrity())),
                put("/api/v1/admin/settings/SELFIE_LIMIT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perDay\":1}"),
                get("/api/v1/admin/audits")
                        .param("from", today.minusDays(1).toString())
                        .param("to", today.toString()));
    }

    @Test
    @DisplayName("토큰 없음·위조·화이트리스트 밖·회원 JWT면 세 관리 요청 모두 403")
    void adminRequests_areForbiddenWithoutOperator() throws Exception {
        String stranger =
                operatorTokens.issue(
                        new OperatorId(new SocialAccount(ProviderType.KAKAO, "stranger")));
        String member = signUp().bearer();
        for (MockHttpServletRequestBuilder request : threeAdminRequests()) {
            mvc.perform(request).andExpect(status().isForbidden());
        }
        for (String header :
                List.of("Bearer forged.token.value", "Bearer " + stranger, member, "Basic x")) {
            for (MockHttpServletRequestBuilder request : threeAdminRequests()) {
                mvc.perform(request.header("Authorization", header))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
            }
        }
    }

    @Test
    @DisplayName("같은 접속지에서 로그인이 10분에 10번 실패하면 429 TOO_MANY_ATTEMPTS, 다른 접속지는 그대로")
    void login_isRateLimitedPerClient() throws Exception {
        String body = write(Map.of("provider", "KAKAO", "providerToken", "stranger"));
        for (int i = 0; i < 10; i++) {
            mvc.perform(
                            post("/api/v1/admin/login")
                                    .header("X-Real-IP", "10.9.9.9")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(
                        post("/api/v1/admin/login")
                                .header("X-Real-IP", "10.9.9.9")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "provider",
                                                        "KAKAO",
                                                        "providerToken",
                                                        OPERATOR))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        mvc.perform(
                        post("/api/v1/admin/login")
                                .header("X-Real-IP", "10.9.9.10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "provider",
                                                        "KAKAO",
                                                        "providerToken",
                                                        OPERATOR))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("화이트리스트 밖 계정의 운영자 로그인은 403, 모르는 제공자는 400")
    void login_rejectsStrangersAndUnknownProviders() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "provider",
                                                        "KAKAO",
                                                        "providerToken",
                                                        "stranger"))))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/admin/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "provider",
                                                        "NAVER",
                                                        "providerToken",
                                                        OPERATOR))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("운영자 토큰으로 유명인 등록·삭제와 기준값 변경을 하면 actorId가 감사 기록에 남는다")
    void operatorActions_areAuditedWithActorId() throws Exception {
        String bearer = "Bearer " + login(OPERATOR);
        JsonNode created =
                body(
                        mvc.perform(
                                        post("/api/v1/admin/celebrities")
                                                .header("Authorization", bearer)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(write(celebrity())))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.outcome").value("REGISTERED"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        long celebrityId = created.get("celebrityId").asLong();
        java.util.Map<String, Object> badUrl = new java.util.HashMap<>(celebrity());
        badUrl.put("sourceUrl", "javascript:alert(1)");
        mvc.perform(
                        post("/api/v1/admin/celebrities")
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(badUrl)))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        delete("/api/v1/admin/celebrities")
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "celebrityId",
                                                        celebrityId,
                                                        "reason",
                                                        "권리자 요청"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DELETED"));
        mvc.perform(
                        put("/api/v1/admin/settings/SELFIE_LIMIT")
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"perDay\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("SELFIE_LIMIT"));

        LocalDate today = LocalDate.now();
        JsonNode audits =
                body(
                        mvc.perform(
                                        get("/api/v1/admin/audits")
                                                .header("Authorization", bearer)
                                                .param("from", today.minusDays(1).toString())
                                                .param("to", today.plusDays(1).toString()))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        List<String> mine = new java.util.ArrayList<>();
        audits.forEach(
                a -> {
                    if (ACTOR.equals(a.get("actorId").asText())) {
                        mine.add(a.get("action").asText());
                    }
                });
        assertThat(mine).contains("CELEBRITY_REGISTER", "CELEBRITY_DELETE", "SETTING_CHANGE");
        mvc.perform(
                        get("/api/v1/admin/audits")
                                .header("Authorization", bearer)
                                .param("action", "CELEBRITY_DELETE")
                                .param("from", today.minusDays(1).toString())
                                .param("to", today.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("CELEBRITY_DELETE"));
        mvc.perform(
                        get("/api/v1/admin/audits")
                                .header("Authorization", bearer)
                                .param("from", today.minusDays(200).toString())
                                .param("to", today.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T-11: LLM_DAILY_LIMIT 전체 201은 거부·200은 허용, SHOP_ITEMS 기존 아이템 삭제는 거부, 지금 값 조회")
    void settingRules() throws Exception {
        String bearer = "Bearer " + login(OPERATOR);
        seedShopItemsLikeShopTests();
        mvc.perform(
                        put("/api/v1/admin/settings/LLM_DAILY_LIMIT")
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"perMember\":1,\"global\":201}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VALUE_INVALID"));
        mvc.perform(
                        put("/api/v1/admin/settings/LLM_DAILY_LIMIT")
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"perMember\":1,\"global\":200}"))
                .andExpect(status().isOk());
        JsonNode current =
                body(
                        mvc.perform(get("/api/v1/admin/settings").header("Authorization", bearer))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        com.fasterxml.jackson.databind.node.ObjectNode shop =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        json.readTree(current.get("SHOP_ITEMS").asText());
        assertThat(shop.withArray("items")).isNotEmpty();
        shop.withArray("items").remove(0);
        mvc.perform(
                        put("/api/v1/admin/settings/SHOP_ITEMS")
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(shop)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VALUE_INVALID"));
        mvc.perform(
                        put("/api/v1/admin/settings/NOT_A_KEY")
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /** 상점 시험(AvatarControllerTest)과 같은 값(901·902)만 넣는다. 비어 있을 때만 넣어 상점 시험의 시드와 겹치지 않는다. */
    private void seedShopItemsLikeShopTests() {
        if (policy.shopCatalog().items().isEmpty()) {
            settings.change(
                    "ops@runkk",
                    SettingKey.SHOP_ITEMS,
                    "{\"items\":[{\"id\":901,\"name\":\"러닝 모자\",\"slot\":\"HAT\",\"price\":30,"
                            + "\"onSale\":true},{\"id\":902,\"name\":\"반바지\",\"slot\":\"BOTTOM\","
                            + "\"price\":40,\"onSale\":true}]}");
        }
    }
}
