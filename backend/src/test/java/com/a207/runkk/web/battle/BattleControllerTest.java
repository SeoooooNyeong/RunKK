package com.a207.runkk.web.battle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 5.7.1 대결 엔드포인트 전부(/battles/*)와 web.runimal(/runimal, /runimal/card). 인증 필터를 거친다. */
@DisplayName("web.battle 대결 엔드포인트")
class BattleControllerTest extends WebTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 20, 7, 0);
    private static final long ASYNC_WAIT_NANOS = 15_000_000_000L;
    private static final long POLL_MILLIS = 100;

    private static String at(LocalDateTime t) {
        return t.atZone(SEOUL).toOffsetDateTime().toString();
    }

    private static Map<String, Object> run(long meters, int seconds) {
        List<LocationPoint> points =
                RunFixtures.straightLine(START, (double) meters / seconds, seconds);
        Map<String, Object> body = new HashMap<>();
        body.put("recordId", UUID.randomUUID().toString());
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

    private void upload(Session s, long meters, int seconds) throws Exception {
        mvc.perform(
                        as(s, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(run(meters, seconds))))
                .andExpect(status().isOk());
    }

    private String distance(long meters) throws Exception {
        return write(Map.of("distanceMeters", meters));
    }

    @Test
    @DisplayName("POST·DELETE /battles/match: 거리 오류·언랭 거부, 대기 중 아니면 대기해제")
    void match_rejectsAndCancels() throws Exception {
        Session s = signUp();
        mvc.perform(
                        as(s, post("/api/v1/battles/match"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(distance(2_000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BATTLE_DISTANCE_INVALID"));
        mvc.perform(
                        as(s, post("/api/v1/battles/match"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(distance(5_000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RANKED_ONLY"));
        mvc.perform(as(s, delete("/api/v1/battles/match")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting").value(false));
        mvc.perform(post("/api/v1/battles/match")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("랭크 회원은 대기 등록되고 두 번째 요청은 이미 대기 중, 취소하면 대기해제")
    void rankedMember_waitsAndCancels() throws Exception {
        Session s = signUp();
        for (int i = 0; i < 3; i++) {
            upload(s, 5_000, 1_500);
        }
        mvc.perform(
                        as(s, post("/api/v1/battles/match"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(distance(5_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting").value(true))
                .andExpect(jsonPath("$.distanceMeters").value(5_000));
        mvc.perform(
                        as(s, post("/api/v1/battles/match"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(distance(5_000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_IN_BATTLE"));
        mvc.perform(as(s, delete("/api/v1/battles/match")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting").value(false));
    }

    @Test
    @DisplayName("나와의 싸움: 후보 → 기록 없음 거부 → 시작 → 시작 보고 → 포기 → 결과·이력")
    void selfBattle_flow() throws Exception {
        Session s = signUp();
        mvc.perform(as(s, get("/api/v1/battles/self/candidates")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(4))
                .andExpect(jsonPath("$.candidates[0].displayName").value("아직 기록이 없어요"));
        mvc.perform(
                        as(s, post("/api/v1/battles/self"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(distance(5_000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_RECORD"));

        upload(s, 5_000, 1_500);
        String started =
                mvc.perform(
                                as(s, post("/api/v1/battles/self"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(distance(5_000)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.cardKind").value("SELF_RUN"))
                        .andExpect(jsonPath("$.opponentName").value("10/20의 나"))
                        .andExpect(jsonPath("$.track[0].elapsedSeconds").value(0))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long battleId = body(started).get("battleId").asLong();

        mvc.perform(as(s, post("/api/v1/battles/" + battleId + "/started")))
                .andExpect(status().isNoContent());
        mvc.perform(as(s, post("/api/v1/battles/" + battleId + "/forfeit")))
                .andExpect(status().isNoContent());
        mvc.perform(as(s, get("/api/v1/battles/" + battleId + "/result")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("LOSE"))
                .andExpect(jsonPath("$.pending").value(false))
                .andExpect(jsonPath("$.type").value("SELF"));
        mvc.perform(as(s, get("/api/v1/battles/history").param("type", "SELF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.losses").value(1))
                .andExpect(jsonPath("$.entries[0].opponentName").value("10/20의 나"));

        Session other = signUp();
        mvc.perform(as(other, get("/api/v1/battles/" + battleId + "/result")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("유명인 대결: 후보 목록, 지워진 기록은 고를 수 없음, 없는 대결은 대결 없음")
    void celebrity_andMissingBattle() throws Exception {
        Session s = signUp();
        mvc.perform(
                        as(s, get("/api/v1/battles/celebrity/candidates"))
                                .param("distanceMeters", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isArray());
        mvc.perform(
                        as(s, post("/api/v1/battles/celebrity"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("celebrityId", 999_999))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CELEBRITY_REMOVED"));
        mvc.perform(as(s, get("/api/v1/battles/999999/result")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BATTLE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /runimal: 임시 유형까지 3회, 유형이 없으면 카드는 NO_TYPE")
    void runimal_statusAndCard() throws Exception {
        Session s = signUp();
        mvc.perform(as(s, get("/api/v1/runimal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingRuns").value(3))
                .andExpect(jsonPath("$.allTypes.length()").value(16));
        mvc.perform(as(s, get("/api/v1/runimal/card")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_TYPE"));
    }

    @Test
    @DisplayName("유효 러닝 3회 뒤 GET /runimal은 임시 유형, GET /runimal/card는 축 비율이 있는 카드")
    void runimal_provisionalCard() throws Exception {
        Session s = signUp();
        for (int i = 0; i < 3; i++) {
            upload(s, 2_000, 720);
        }
        long deadline = System.nanoTime() + ASYNC_WAIT_NANOS;
        String type = null;
        while (type == null && System.nanoTime() < deadline) {
            JsonNode status =
                    body(
                            mvc.perform(as(s, get("/api/v1/runimal")))
                                    .andReturn()
                                    .getResponse()
                                    .getContentAsString());
            type = status.get("type").isNull() ? null : status.get("type").asText();
            Thread.sleep(POLL_MILLIS);
        }
        org.assertj.core.api.Assertions.assertThat(type).isNotNull();
        mvc.perform(as(s, get("/api/v1/runimal")))
                .andExpect(jsonPath("$.provisional").value(true))
                .andExpect(jsonPath("$.remainingRuns").value(7));
        mvc.perform(as(s, get("/api/v1/runimal/card")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value(type))
                .andExpect(jsonPath("$.axes.length()").value(4))
                .andExpect(jsonPath("$.axisRatios.DISTANCE").value(1.0));
    }
}
