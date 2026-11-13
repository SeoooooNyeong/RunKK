package com.a207.runkk.web.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.analysis.RankingService;
import com.a207.runkk.avatar.Avatar;
import com.a207.runkk.avatar.AvatarRepository;
import com.a207.runkk.avatar.BodyShape;
import com.a207.runkk.avatar.DefaultCharacter;
import com.a207.runkk.avatar.SkinTone;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * web.record 5.7.1 엔드포인트 전부(대시보드·기록 목록·상세·카드·성장·리포트·랭킹·주간 목표·계획). 기록은 실제 POST /runs로 올리고, 리포트는 커밋 뒤
 * 비동기 수신자가 만든다(ai 서버가 없어 규칙 문구로 대체된다, FR-066).
 */
@DisplayName("web.record 기록 분석 화면")
class RecordControllerTest extends WebTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.now(SEOUL);
    private static final LocalDateTime START = TODAY.atTime(6, 0);
    private static final int POLL_MILLIS = 100;
    private static final int POLL_TIMES = 100;

    @Autowired RankingService rankingService;
    @Autowired AvatarRepository avatars;
    @Autowired OperationSettingService settings;
    @Autowired OperationPolicy policy;
    @Autowired TransactionTemplate tx;

    private static String at(LocalDateTime t) {
        return t.atZone(SEOUL).toOffsetDateTime().toString();
    }

    private String upload(Session s, long meters, int seconds) throws Exception {
        String recordId = UUID.randomUUID().toString();
        List<LocationPoint> points =
                RunFixtures.straightLine(START, (double) meters / seconds, seconds);
        Map<String, Object> body = new HashMap<>();
        body.put("recordId", recordId);
        body.put("startedAt", at(START));
        body.put("endedAt", at(START.plusSeconds(seconds)));
        body.put("distanceMeters", meters);
        body.put("elapsedMillis", seconds * 1000L);
        body.put("mode", "FREE");
        body.put("cardKind", "FREE_RUN");
        body.put("pointCount", points.size());
        body.put(
                "points",
                points.stream()
                        .map(
                                p ->
                                        Map.of(
                                                "measuredAt", at(p.measuredAt()),
                                                "latitude", p.point().latitude(),
                                                "longitude", p.point().longitude(),
                                                "accuracyMeters", 5))
                        .toList());
        mvc.perform(
                        as(s, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(body)))
                .andExpect(status().isOk());
        return recordId;
    }

    private JsonNode getJson(Session s, String url, Object... params) throws Exception {
        var request = as(s, get(url));
        for (int i = 0; i < params.length; i += 2) {
            request.param((String) params[i], String.valueOf(params[i + 1]));
        }
        return body(
                mvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    @Test
    @DisplayName("대시보드·목록·상세·카드·성장: 업로드한 기록이 보이고 본인 전용 항목은 본인에게만")
    void dashboardListDetailCardGrowth() throws Exception {
        Session me = signUp();
        Session other = signUp();
        String recordId = upload(me, 5_000, 1_500);

        JsonNode dashboard = getJson(me, "/api/v1/dashboard", "baseDate", TODAY);
        assertThat(dashboard.get("todayMeters").asLong()).isEqualTo(5_000);
        assertThat(dashboard.get("todayRuns").asInt()).isEqualTo(1);
        assertThat(dashboard.get("streakDays").asInt()).isEqualTo(1);
        assertThat(dashboard.get("streakWeeks").asInt()).isEqualTo(1);
        assertThat(dashboard.get("landmark").asText()).isNotBlank();
        assertThat(dashboard.get("grass").get(TODAY.toString()).asLong()).isEqualTo(5_000);
        assertThat(dashboard.get("calendar").get(TODAY.toString()).get(0).get("recordId").asText())
                .isEqualTo(recordId);
        assertThat(dashboard.get("weekly").isNull()).isTrue();
        assertThat(dashboard.get("weeklyAchievement").isNull()).isTrue();
        // 주간 목표가 없어도 이번 주 유효 횟수·거리는 준다(실기기 DV3-4)
        assertThat(dashboard.get("weekRuns").asInt()).isEqualTo(1);
        assertThat(dashboard.get("weekMeters").asLong()).isEqualTo(5_000);

        JsonNode list =
                getJson(
                        other,
                        "/api/v1/members/" + me.memberId() + "/runs",
                        "from",
                        TODAY.minusDays(7),
                        "to",
                        TODAY);
        assertThat(list.get("runs").get(0).get("recordId").asText()).isEqualTo(recordId);
        assertThat(list.get("runs").get(0).get("averagePaceSecondsPerKm").asInt()).isEqualTo(300);
        assertThat(list.get("runs").get(0).get("startedAt").asText()).endsWith("+09:00");

        JsonNode own = getJson(me, "/api/v1/runs/" + recordId);
        assertThat(own.get("owner").asBoolean()).isTrue();
        assertThat(own.get("route")).isNotEmpty();
        assertThat(own.get("calories").asInt()).isEqualTo(325);
        assertThat(own.get("rewardPoints").asInt()).isEqualTo(50);
        JsonNode theirs = getJson(other, "/api/v1/runs/" + recordId);
        assertThat(theirs.get("owner").asBoolean()).isFalse();
        assertThat(theirs.get("rewardPoints").isNull()).isTrue();

        JsonNode card = getJson(me, "/api/v1/runs/" + recordId + "/card");
        assertThat(card.get("cardKind").asText()).isEqualTo("FREE_RUN");
        assertThat(card.get("route")).isNotEmpty();
        assertThat(card.get("nickname").asText()).startsWith("웹러너");
        assertThat(own.get("recordVisibility").asText()).isEqualTo("PUBLIC");
        assertThat(theirs.get("recordVisibility").isNull()).isTrue();
        mvc.perform(as(other, get("/api/v1/runs/" + recordId + "/card")))
                .andExpect(status().isForbidden());

        JsonNode growth = getJson(me, "/api/v1/growth", "period", "WEEKLY");
        assertThat(growth.get("periods")).hasSize(8);
        assertThat(growth.get("bests").get("KM5").asLong()).isEqualTo(1_500);
        assertThat(growth.get("longestMeters").asLong()).isEqualTo(5_000);
        assertThat(getJson(me, "/api/v1/growth", "period", "MONTHLY").get("periods")).hasSize(6);
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비공개 기록은 409 RECORD_PRIVATE, 크루 공개는 다른 크루면 409 RECORD_CREW_ONLY")
    void visibility() throws Exception {
        Session me = signUp();
        Session other = signUp();
        String recordId = upload(me, 3_000, 1_000);
        mvc.perform(
                        as(me, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "item",
                                                        "RECORD_VISIBILITY",
                                                        "value",
                                                        "PRIVATE"))))
                .andExpect(status().isOk());
        mvc.perform(as(other, get("/api/v1/runs/" + recordId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_PRIVATE"));
        mvc.perform(
                        as(me, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "item",
                                                        "RECORD_VISIBILITY",
                                                        "value",
                                                        "CREW"))))
                .andExpect(status().isOk());
        mvc.perform(
                        as(other, get("/api/v1/members/" + me.memberId() + "/runs"))
                                .param("from", TODAY.minusDays(1).toString())
                                .param("to", TODAY.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_CREW_ONLY"));
        mvc.perform(
                        as(me, get("/api/v1/members/" + me.memberId() + "/runs"))
                                .param("from", TODAY.minusDays(400).toString())
                                .param("to", TODAY.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("리포트: 없으면 '유효 러닝 기록 없음', 업로드 뒤 규칙 문구(ai 서버 없음 → 대체)")
    void report() throws Exception {
        Session me = signUp();
        assertThat(getJson(me, "/api/v1/report").get("status").asText()).isEqualTo("NO_VALID_RUN");
        upload(me, 5_000, 1_500);
        JsonNode report = null;
        for (int i = 0; i < POLL_TIMES; i++) {
            report = getJson(me, "/api/v1/report");
            if ("READY".equals(report.get("status").asText())) {
                break;
            }
            Thread.sleep(POLL_MILLIS);
        }
        assertThat(report.get("status").asText()).isEqualTo("READY");
        assertThat(report.get("commentSource").asText()).isEqualTo("RULE");
        assertThat(report.get("toRun").asInt()).isEqualTo(1);
        assertThat(report.get("summary").asText()).startsWith("1~1회 평균 점수");
    }

    @Test
    @DisplayName("랭킹: 0시 재집계 뒤 본인 줄·집계 기준 시각(+09:00), 크루 없음 안내")
    void rankings() throws Exception {
        Session me = signUp();
        upload(me, 7_000, 2_100);
        rankingService.rebuild(LocalDateTime.now(SEOUL));
        JsonNode personal = getJson(me, "/api/v1/rankings/personal", "period", "WEEKLY");
        assertThat(personal.get("aggregatedAt").asText()).endsWith("+09:00");
        assertThat(personal.get("mine").get("subjectId").asLong()).isEqualTo(me.memberId());
        assertThat(personal.get("mine").get("distanceMeters").asLong()).isEqualTo(7_000);
        JsonNode crew = getJson(me, "/api/v1/rankings/crew", "period", "MONTHLY");
        assertThat(crew.get("mine").isNull()).isTrue();
        assertThat(crew.get("notice").asText()).contains("크루");
    }

    @Test
    @DisplayName(
            "TC-5801 랭킹 행 publicTier: 조회 때 회원의 지금 티어(배치 중 UNRANKED → 확정 뒤 바로 반영), 스냅숏 tier는"
                    + " 그대로(D-486)")
    void ranking_carriesPublicTier() throws Exception {
        Session me = signUp();
        upload(me, 7_000, 2_100);
        rankingService.rebuild(LocalDateTime.now(SEOUL));
        JsonNode before = getJson(me, "/api/v1/rankings/personal", "period", "WEEKLY").get("mine");
        assertThat(before.get("publicTier").asText()).isEqualTo("UNRANKED");
        assertThat(before.get("tier").asText()).isEqualTo("UNRANKED");

        rank(me, com.a207.runkk.common.Tier.GOLD);
        JsonNode after = getJson(me, "/api/v1/rankings/personal", "period", "WEEKLY").get("mine");
        assertThat(after.get("publicTier").asText()).isEqualTo("GOLD");
        assertThat(after.get("tier").asText()).as("스냅숏 값(0시) 그대로").isEqualTo("UNRANKED");
        assertThat(after.has("provisionalTier")).isFalse();
        JsonNode crew = getJson(me, "/api/v1/rankings/crew", "period", "MONTHLY");
        assertThat(crew.get("mine").isNull()).isTrue();
    }

    /** 랭킹 look 시험 아이템(S15P31A207-300): 그림 키 있는 상의, 그림 키 없는 신발. */
    private static final String TEE_961 =
            "{\"id\":961,\"name\":\"크루 티\",\"slot\":\"TOP\",\"price\":10,\"onSale\":true,"
                    + "\"assetKey\":\"top_crew_tee\"}";

    private static final String SHOES_962 =
            "{\"id\":962,\"name\":\"그림 없는 신발\",\"slot\":\"SHOES\",\"price\":10,\"onSale\":true}";

    /** 시험 문맥을 함께 쓰는 다른 시험이 넣은 아이템은 지우지 못하므로(D-44) 지금 목록에 없는 시험 아이템만 덧붙인다. */
    private void ensureLookItems() throws Exception {
        List<String> items = new java.util.ArrayList<>();
        java.util.Set<Long> have = new java.util.HashSet<>();
        for (var item : policy.shopCatalog().items()) {
            have.add(item.id());
            items.add(write(item));
        }
        boolean added = false;
        for (var e : Map.of(961L, TEE_961, 962L, SHOES_962).entrySet()) {
            if (!have.contains(e.getKey())) {
                items.add(e.getValue());
                added = true;
            }
        }
        if (added) {
            settings.change(
                    "ops@runkk",
                    SettingKey.SHOP_ITEMS,
                    "{\"items\":[" + String.join(",", items) + "]}");
        }
    }

    @Test
    @DisplayName(
            "랭킹 줄 look(S15P31A207-300): 개인 랭킹 줄과 mine에 얼굴 키·착용 그림 키·3단계 체형·러닝량, 아바타 행이 없으면"
                    + " 기본값, 크루 랭킹은 null")
    void ranking_carriesRunnerLook() throws Exception {
        ensureLookItems();
        Session me = signUp();
        Session other = signUp();
        upload(me, 7_000, 2_100);
        upload(other, 3_000, 900);
        tx.executeWithoutResult(
                status -> {
                    Avatar avatar = avatars.findForUpdate(me.memberId()).orElseThrow();
                    avatar.chooseDefault(DefaultCharacter.BEAR);
                    avatar.equip(961, ItemSlot.TOP);
                    avatar.equip(962, ItemSlot.SHOES);
                    avatar.updateAppearance(BodyShape.STAGE1, SkinTone.NIGHT);
                    avatars.save(avatar);
                });
        rankingService.rebuild(LocalDateTime.now(SEOUL));

        JsonNode personal = getJson(me, "/api/v1/rankings/personal", "period", "WEEKLY");
        JsonNode look = personal.get("mine").get("look");
        assertThat(look.fieldNames())
                .toIterable()
                .containsExactly("faceKey", "outfit", "bodyVariant", "runAmount");
        assertThat(look.get("faceKey").asText()).isEqualTo("default:BEAR");
        assertThat(look.get("outfit").fieldNames())
                .as("그림 키 없는 신발은 빠진다")
                .toIterable()
                .containsExactly("TOP");
        assertThat(look.get("outfit").get("TOP").asText()).isEqualTo("top_crew_tee");
        assertThat(look.get("bodyVariant").asInt()).isEqualTo(2);
        assertThat(look.get("runAmount").asInt()).isEqualTo(23);
        for (JsonNode entry : personal.get("entries")) {
            assertThat(entry.get("look").isNull())
                    .as("탈퇴(공개 티어 없음) 회원 줄만 look이 null이다: " + entry)
                    .isEqualTo(entry.get("publicTier").isNull());
        }

        JsonNode fresh =
                getJson(other, "/api/v1/rankings/personal", "period", "WEEKLY")
                        .get("mine")
                        .get("look");
        assertThat(fresh.get("faceKey").isNull()).isTrue();
        assertThat(fresh.get("outfit").isObject()).isTrue();
        assertThat(fresh.get("outfit").isEmpty()).isTrue();
        assertThat(fresh.get("bodyVariant").asInt()).isZero();
        assertThat(fresh.get("runAmount").asInt()).isEqualTo(10);
        assertThat(avatars.findAllByMemberIds(List.of(other.memberId())))
                .as("조회 경로는 아바타 행을 만들지 않는다")
                .isEmpty();

        JsonNode crew = getJson(me, "/api/v1/rankings/crew", "period", "WEEKLY");
        for (JsonNode entry : crew.get("entries")) {
            assertThat(entry.get("look").isNull()).isTrue();
        }
    }

    @Test
    @DisplayName("주간 목표·계획: 저장, 범위 위반 409, 누락 400, 오늘 계획 없으면 204, 이행 현황")
    void goalsAndPlans() throws Exception {
        Session me = signUp();
        mvc.perform(
                        as(me, put("/api/v1/goals/weekly"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("runs", 3, "distanceMeters", 15_000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
        mvc.perform(
                        as(me, put("/api/v1/goals/weekly"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("runs", 9, "distanceMeters", 15_000))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOAL_INVALID"));
        mvc.perform(
                        as(me, put("/api/v1/goals/weekly"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("runs", 3))))
                .andExpect(status().isBadRequest());
        mvc.perform(as(me, get("/api/v1/plans/today"))).andExpect(status().isNoContent());
        mvc.perform(
                        as(me, put("/api/v1/plans/" + TODAY.minusDays(1)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("distanceMeters", 3_000))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAST_DATE"));
        mvc.perform(
                        as(me, put("/api/v1/plans/" + TODAY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "distanceMeters",
                                                        4_200,
                                                        "paceSecondsPerKm",
                                                        330))))
                .andExpect(status().isOk());
        JsonNode today = getJson(me, "/api/v1/plans/today");
        assertThat(today.get("distanceMeters").asLong()).isEqualTo(4_200);
        assertThat(today.get("supplementMeters").asLong()).isEqualTo(5_000);
        upload(me, 2_000, 700);
        JsonNode progress =
                getJson(me, "/api/v1/plans/progress", "from", TODAY.minusDays(2), "to", TODAY);
        JsonNode todayRow = progress.get("days").get(2);
        assertThat(todayRow.get("plannedMeters").asLong()).isEqualTo(4_200);
        assertThat(todayRow.get("doneMeters").asLong()).isEqualTo(2_000);
        assertThat(todayRow.get("shortfallMeters").asLong()).isEqualTo(2_200);
        JsonNode dashboard = getJson(me, "/api/v1/dashboard");
        assertThat(dashboard.get("weekly").get("goalRuns").asInt()).isEqualTo(3);
        assertThat(dashboard.get("weeklyAchievement").asDouble()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("계획 취소: DELETE /plans/{date}는 204, 다시 불러도 204, 지난 날짜는 409 PAST_DATE")
    void deletePlan() throws Exception {
        Session me = signUp();
        mvc.perform(
                        as(me, put("/api/v1/plans/" + TODAY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("distanceMeters", 3_000))))
                .andExpect(status().isOk());

        mvc.perform(as(me, delete("/api/v1/plans/" + TODAY))).andExpect(status().isNoContent());
        mvc.perform(as(me, delete("/api/v1/plans/" + TODAY))).andExpect(status().isNoContent());

        mvc.perform(as(me, get("/api/v1/plans/today"))).andExpect(status().isNoContent());
        mvc.perform(as(me, delete("/api/v1/plans/" + TODAY.minusDays(1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAST_DATE"));
    }

    @Test
    @DisplayName(
            "TC-7103·TC-6682: 정지 두 번 있는 자유런 상세에 pauses 2개(+09:00)와 pausedSeconds 150, 경과는 운동 시간")
    void detail_showsPauses() throws Exception {
        Session me = signUp();
        String recordId = UUID.randomUUID().toString();
        int wallSeconds = 35 * 60;
        List<LocationPoint> points = RunFixtures.straightLine(START, 5_000.0 / 1_950, wallSeconds);
        Map<String, Object> body = new HashMap<>();
        body.put("recordId", recordId);
        body.put("startedAt", at(START));
        body.put("endedAt", at(START.plusSeconds(wallSeconds)));
        body.put("distanceMeters", 5_000);
        body.put("elapsedMillis", 1_950_000L);
        body.put("mode", "FREE");
        body.put("cardKind", "FREE_RUN");
        body.put("pointCount", points.size());
        body.put(
                "points",
                points.stream()
                        .map(
                                p ->
                                        Map.of(
                                                "measuredAt", at(p.measuredAt()),
                                                "latitude", p.point().latitude(),
                                                "longitude", p.point().longitude(),
                                                "accuracyMeters", 5))
                        .toList());
        body.put(
                "pauses",
                List.of(
                        Map.of(
                                "from", at(START.plusMinutes(10)),
                                "to", at(START.plusMinutes(12))),
                        Map.of(
                                "from", at(START.plusSeconds(20 * 60 + 30)),
                                "to", at(START.plusMinutes(21)))));
        mvc.perform(
                        as(me, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(body)))
                .andExpect(status().isOk());

        JsonNode detail = getJson(me, "/api/v1/runs/" + recordId);
        assertThat(detail.get("pauses")).hasSize(2);
        assertThat(detail.get("pauses").get(0).get("from").asText()).endsWith("+09:00");
        assertThat(
                        java.time.OffsetDateTime.parse(
                                        detail.get("pauses").get(1).get("to").asText())
                                .toInstant())
                .isEqualTo(START.plusMinutes(21).atZone(SEOUL).toInstant());
        assertThat(detail.get("pausedSeconds").asLong()).isEqualTo(150);
        assertThat(detail.get("summary").get("elapsedSeconds").asLong()).isEqualTo(1_950);

        JsonNode plain = getJson(me, "/api/v1/runs/" + upload(me, 5_000, 1_500));
        assertThat(plain.get("pauses")).isEmpty();
        assertThat(plain.get("pausedSeconds").asLong()).isZero();
    }
}
