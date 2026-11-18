package com.a207.runkk.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.RunFixtures;
import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * v0.14 서버 엔드포인트(설계 5.6.3): 친구 대결 초대 6개, 기록 삭제, 기록 목록·상세·카드·대시보드 새 필드, Runimal 16유형, 가이드, 프로필. 오류는
 * 409 {code, message}, 권한은 403 PERMISSION_DENIED(D-227, test.md TC-9551~TC-9562, TC-9851·TC-9852).
 */
@DisplayName("v0.14 엔드포인트(친구 대결·기록 삭제·Runimal 유형·가이드)")
class FigmaV014ControllerTest extends WebTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 20, 7, 0);
    private static final String INVITES = "/api/v1/battles/friend-invites";

    private static String at(LocalDateTime t) {
        return t.atZone(SEOUL).toOffsetDateTime().toString();
    }

    private static Map<String, Object> run(String recordId, long meters, int seconds) {
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
        return body;
    }

    private String upload(Session s, long meters, int seconds) throws Exception {
        String recordId = UUID.randomUUID().toString();
        mvc.perform(
                        as(s, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(run(recordId, meters, seconds))))
                .andExpect(status().isOk());
        return recordId;
    }

    private JsonNode invite(Session s, long meters) throws Exception {
        return body(
                mvc.perform(
                                as(s, post(INVITES))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(write(Map.of("distanceMeters", meters))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    @Test
    @DisplayName(
            "TC-9556·TC-9558 초대 만들기 201·형식, 거리 오류·중복 대기 409, mine 200/204, 취소 204 멱등·남의 초대 403·없음"
                    + " 409")
    void inviteCreateMineCancel() throws Exception {
        Session a = signUp();
        Session b = signUp();
        mvc.perform(
                        as(a, post(INVITES))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("distanceMeters", 2_000))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BATTLE_DISTANCE_INVALID"));

        JsonNode created = invite(a, 3_000);
        long inviteId = created.get("inviteId").asLong();
        String token = created.get("token").asText();
        org.assertj.core.api.Assertions.assertThat(token).matches("^[A-Za-z0-9_-]{22}$");
        org.assertj.core.api.Assertions.assertThat(created.get("inviteUrl").asText())
                .isEqualTo("https://k15a207.p.ssafy.io/invite/battle/" + token);
        org.assertj.core.api.Assertions.assertThat(created.get("distanceMeters").asLong())
                .isEqualTo(3_000);
        org.assertj.core.api.Assertions.assertThat(created.get("expiresAt").asText()).isNotBlank();

        mvc.perform(
                        as(a, post(INVITES))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("distanceMeters", 3_000))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITE_ALREADY_PENDING"));
        mvc.perform(
                        as(a, post("/api/v1/battles/match"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("distanceMeters", 3_000))))
                .andExpect(status().isConflict());

        mvc.perform(as(a, get(INVITES + "/mine")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteId").value(inviteId))
                .andExpect(jsonPath("$.token").value(token));
        mvc.perform(as(b, delete(INVITES + "/" + inviteId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mvc.perform(as(a, delete(INVITES + "/" + inviteId))).andExpect(status().isNoContent());
        mvc.perform(as(a, delete(INVITES + "/" + inviteId))).andExpect(status().isNoContent());
        mvc.perform(as(a, delete(INVITES + "/999999")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
        mvc.perform(as(a, get(INVITES + "/mine"))).andExpect(status().isNoContent());

        mvc.perform(as(b, get(INVITES + "/by-token/" + token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.blockedReason").value("INVITE_CANCELLED"));
    }

    @Test
    @DisplayName(
            "TC-9551·TC-9554·TC-9559 링크 조회·참가·거절: 본인 INVITE_OWN, 참가 200 battleId, 두 번째 참가"
                    + " INVITE_ALREADY_JOINED, 없는 토큰 409")
    void inviteViewAcceptDecline() throws Exception {
        Session a = signUp();
        Session b = signUp();
        Session c = signUp();
        String token = invite(a, 1_000).get("token").asText();

        mvc.perform(as(b, get(INVITES + "/by-token/" + token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.distanceMeters").value(1_000))
                .andExpect(jsonPath("$.inviter.name").value(notNullValue()))
                .andExpect(jsonPath("$.inviter.tier").value("UNRANKED"))
                .andExpect(jsonPath("$.inviter.bestSeconds").value(nullValue()))
                .andExpect(jsonPath("$.me.tier").value("UNRANKED"))
                .andExpect(jsonPath("$.blockedReason").value(nullValue()));
        mvc.perform(as(a, get(INVITES + "/by-token/" + token)))
                .andExpect(jsonPath("$.blockedReason").value("INVITE_OWN"));
        mvc.perform(as(a, post(INVITES + "/by-token/" + token + "/accept")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITE_OWN"))
                .andExpect(jsonPath("$.message").value(notNullValue()));
        mvc.perform(as(b, get(INVITES + "/by-token/nope")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));

        mvc.perform(as(b, post(INVITES + "/by-token/" + token + "/accept")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.battleId").value(notNullValue()));
        mvc.perform(as(c, post(INVITES + "/by-token/" + token + "/accept")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITE_ALREADY_JOINED"));
        mvc.perform(as(c, post(INVITES + "/by-token/" + token + "/decline")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITE_ALREADY_JOINED"));
        mvc.perform(as(a, get("/api/v1/battles/active")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FRIEND"));
        mvc.perform(as(a, post("/api/v1/battles/" + activeBattle(a) + "/forfeit")))
                .andExpect(status().isNoContent());

        Session d = signUp();
        Session e = signUp();
        String second = invite(d, 5_000).get("token").asText();
        mvc.perform(as(e, post(INVITES + "/by-token/" + second + "/decline")))
                .andExpect(status().isNoContent());
        mvc.perform(as(e, post(INVITES + "/by-token/" + second + "/accept")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITE_CANCELLED"));
    }

    private long activeBattle(Session s) throws Exception {
        return body(mvc.perform(as(s, get("/api/v1/battles/active")))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("battleId")
                .asLong();
    }

    @Test
    @DisplayName(
            "TC-9560·TC-9824 기록 삭제: 집계 제외 204 뒤 409 RECORD_NOT_FOUND, 유효 409"
                    + " RECORD_VALID_NOT_DELETABLE, 남의 기록 403, 비로그인 401")
    void deleteRun() throws Exception {
        Session a = signUp();
        Session b = signUp();
        String invalid = upload(a, 900, 300);
        String valid = upload(a, 5_000, 1_500);

        mvc.perform(as(a, get("/api/v1/runs/" + invalid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletable").value(true))
                .andExpect(jsonPath("$.evaluation").value(nullValue()));
        mvc.perform(as(a, get("/api/v1/runs/" + valid)))
                .andExpect(jsonPath("$.deletable").value(false));
        mvc.perform(delete("/api/v1/runs/" + invalid)).andExpect(status().isUnauthorized());
        mvc.perform(as(b, delete("/api/v1/runs/" + invalid)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mvc.perform(as(a, delete("/api/v1/runs/" + valid)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_VALID_NOT_DELETABLE"));
        mvc.perform(as(a, delete("/api/v1/runs/" + invalid))).andExpect(status().isNoContent());
        mvc.perform(as(a, delete("/api/v1/runs/" + invalid)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
        mvc.perform(as(a, get("/api/v1/runs/" + invalid)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));

        // TC-9822 묘비: 같은 기록 ID를 다시 올리면 저장됨으로 알리고 되살리지 않는다
        mvc.perform(
                        as(a, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(run(invalid, 900, 300))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_STORED"));
        mvc.perform(as(a, get("/api/v1/runs/" + invalid))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName(
            "TC-9852 기록 목록·카드·대시보드 새"
                    + " 필드(newRecords·routeShape·battleType·temperatureC·rewardBalance·monthRuns)")
    void recordFields() throws Exception {
        Session a = signUp();
        upload(a, 3_000, 1_200); // 첫 기록은 신기록 표시가 없다
        String valid = upload(a, 5_000, 1_500);
        mvc.perform(
                        as(
                                a,
                                get("/api/v1/members/" + a.memberId() + "/runs")
                                        .param("from", "2026-10-01")
                                        .param("to", "2026-10-31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs", hasSize(2)))
                .andExpect(jsonPath(row(valid, "battleType")).value(contains(nullValue())))
                .andExpect(jsonPath(row(valid, "verdict")).value(contains(nullValue())))
                .andExpect(jsonPath(row(valid, "pending")).value(contains(false)))
                .andExpect(jsonPath(row(valid, "newRecord")).value(contains(true)))
                .andExpect(
                        jsonPath(row(valid, "newRecords[0].kind")).value(contains(notNullValue())))
                .andExpect(jsonPath(row(valid, "routeShape[0]")).value(contains(hasSize(2))))
                .andExpect(jsonPath(row(valid, "valid")).value(contains(true)));
        mvc.perform(as(a, get("/api/v1/runs/" + valid + "/card")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperatureC").hasJsonPath())
                .andExpect(jsonPath("$.sky").hasJsonPath());
        mvc.perform(as(a, get("/api/v1/dashboard").param("baseDate", "2026-10-20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardBalance").value(notNullValue()))
                .andExpect(jsonPath("$.monthRuns").value(2))
                .andExpect(jsonPath("$.monthMeters").value(8_000));
        mvc.perform(as(a, get("/api/v1/battles/self/candidates"))).andExpect(status().isOk());

        // TC-9417(v0.18 변경): 유효 3회째 기록은 첫 배치 평가를 남기지 않는다(배치고사 3판으로만, J24-6·D-484)
        String third = upload(a, 5_000, 1_400);
        mvc.perform(as(a, get("/api/v1/runs/" + third)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation").value(nullValue()));
        mvc.perform(as(a, get("/api/v1/runs/" + valid)))
                .andExpect(jsonPath("$.evaluation").value(nullValue()));
    }

    @Autowired private com.a207.runkk.running.RunRecordRepository runRecords;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    @DisplayName(
            "S15P31A207-331 재평가 표시를 저장하면 기록 상세 evaluation.previousTier에 바꾸기 전 티어가 나가고, 직전 티어 없이 저장한"
                    + " 기록은 null")
    void evaluationPreviousTier_isStoredAndShown() throws Exception {
        Session a = signUp();
        String withPrevious = upload(a, 5_000, 1_500);
        String withoutPrevious = upload(a, 5_000, 1_500);
        for (String id : List.of(withPrevious, withoutPrevious)) {
            jdbc.update("update run_record set tier_applied = false where record_id = ?", id);
        }
        tx.executeWithoutResult(
                status -> {
                    assertThat(
                                    runRecords.markTierApplied(
                                            withPrevious,
                                            java.util.Optional.of(
                                                    new com.a207.runkk.running.EvaluationView(
                                                            "REEVALUATION",
                                                            com.a207.runkk.common.Tier.GOLD,
                                                            10,
                                                            null,
                                                            com.a207.runkk.common.Tier.SILVER))))
                            .isTrue();
                    assertThat(
                                    runRecords.markTierApplied(
                                            withoutPrevious,
                                            java.util.Optional.of(
                                                    new com.a207.runkk.running.EvaluationView(
                                                            "REEVALUATION",
                                                            com.a207.runkk.common.Tier.GOLD,
                                                            20,
                                                            null,
                                                            null))))
                            .isTrue();
                });

        mvc.perform(as(a, get("/api/v1/runs/" + withPrevious)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation.kind").value("REEVALUATION"))
                .andExpect(jsonPath("$.evaluation.tier").value("GOLD"))
                .andExpect(jsonPath("$.evaluation.previousTier").value("SILVER"));
        mvc.perform(as(a, get("/api/v1/runs/" + withoutPrevious)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation.validRuns").value(20))
                .andExpect(jsonPath("$.evaluation.previousTier").value(nullValue()));
    }

    private static String row(String recordId, String field) {
        return "$.runs[?(@.recordId == '" + recordId + "')]." + field;
    }

    @Test
    @DisplayName("TC-9403·TC-9851 Runimal 16유형 목록·상세, 모르는 유형 400")
    void runimalTypes() throws Exception {
        Session a = signUp();
        mvc.perform(as(a, get("/api/v1/runimal/types")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(nullValue()))
                .andExpect(jsonPath("$.types", hasSize(16)))
                .andExpect(jsonPath("$.types[0].everConfirmed").value(false))
                .andExpect(jsonPath("$.types[0].axes.COMPANION").value(matchesPattern("[AB]")));
        mvc.perform(as(a, get("/api/v1/runimal/types/LONE_WOLF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traits", hasSize(3)))
                .andExpect(jsonPath("$.displayName").value("고독한 늑대"))
                .andExpect(jsonPath("$.sharePercent").hasJsonPath());
        mvc.perform(as(a, get("/api/v1/runimal/types/NOPE"))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-231 가이드 GET·PUT, FR-005·FR-012 프로필 항목별 공개·누적 값·사진 무시")
    void guideAndProfile() throws Exception {
        Session a = signUp();
        Session b = signUp();
        mvc.perform(as(a, get("/api/v1/me/guide")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seen").value(false));
        mvc.perform(as(a, put("/api/v1/me/guide"))).andExpect(status().isNoContent());
        mvc.perform(as(a, put("/api/v1/me/guide"))).andExpect(status().isNoContent());
        mvc.perform(as(a, get("/api/v1/me/guide"))).andExpect(jsonPath("$.seen").value(true));

        mvc.perform(
                        as(a, patch("/api/v1/me/profile"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "birthYear",
                                                        1995,
                                                        "gender",
                                                        "FEMALE",
                                                        "photoUrl",
                                                        "https://x/y.png",
                                                        "ageBandVisibility",
                                                        "PUBLIC",
                                                        "genderVisibility",
                                                        "PRIVATE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value(nullValue()));
        upload(a, 5_000, 1_500);
        mvc.perform(as(b, get("/api/v1/members/" + a.memberId() + "/profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disclosure").value("PUBLIC"))
                .andExpect(jsonPath("$.ageBand").value(notNullValue()))
                .andExpect(jsonPath("$.gender").value(nullValue()))
                .andExpect(jsonPath("$.photoUrl").value(nullValue()))
                .andExpect(jsonPath("$.totalDistanceMeters").value(5_000))
                .andExpect(jsonPath("$.runCount").value(1))
                .andExpect(jsonPath("$.recentRuns", hasSize(1)))
                .andExpect(jsonPath("$.recentRuns[0].kind").value("FREE_RUN"))
                .andExpect(jsonPath("$.ageBandVisibility").value(nullValue()));
        mvc.perform(as(a, get("/api/v1/members/" + a.memberId() + "/profile")))
                .andExpect(jsonPath("$.ageBandVisibility").value("PUBLIC"))
                .andExpect(jsonPath("$.genderVisibility").value("PRIVATE"))
                .andExpect(jsonPath("$.recentRuns", hasSize(0)));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.a207.runkk.reward.RewardReconcileService rewardReconcile;

    @org.springframework.beans.factory.annotation.Autowired
    private com.a207.runkk.battle.BattleRepository battleRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.transaction.support.TransactionTemplate tx;

    @Test
    @DisplayName(
            "TC-9814 친구 대결 승리는 BATTLE_WIN 0: 결과 응답 rewardPoints 0·totalRewardPoints 0, 다음 날 0시 10분"
                    + " 보정 뒤에도 0")
    void friendWinHasNoBattleReward() throws Exception {
        Session a = signUp();
        Session b = signUp();
        String token = invite(a, 5_000).get("token").asText();
        mvc.perform(as(b, post(INVITES + "/by-token/" + token + "/accept")))
                .andExpect(status().isOk());
        long battleId = activeBattle(a);
        // 출발 전 포기는 취소라 결과가 없다(TC-9303). 출발시킨 뒤 포기해 승패를 낸다
        tx.executeWithoutResult(
                status -> {
                    com.a207.runkk.battle.Battle battle =
                            battleRepository.findById(battleId).orElseThrow();
                    LocalDateTime now = LocalDateTime.now();
                    battle.scheduleStart(now);
                    battle.start(now);
                    battleRepository.save(battle);
                });
        // 취소는 셀프런·셀럽런만이다(2026-10-30 결정). 친구 대결은 그만두기로 끝낸다
        mvc.perform(as(a, post("/api/v1/battles/" + battleId + "/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MATCHED"));
        mvc.perform(as(a, post("/api/v1/battles/" + battleId + "/forfeit")))
                .andExpect(status().isNoContent());

        for (int round = 0; round < 2; round++) {
            mvc.perform(as(b, get("/api/v1/battles/" + battleId + "/result")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("FRIEND"))
                    .andExpect(jsonPath("$.verdict").value("WIN"))
                    .andExpect(jsonPath("$.rewardPoints").value(0))
                    .andExpect(jsonPath("$.totalRewardPoints").value(0));
            rewardReconcile.reconcile(
                    LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay().plusMinutes(10));
        }
    }
}
