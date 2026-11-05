package com.a207.runkk.web.battle;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.common.Tier;
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
    @DisplayName(
            "TC-5798 POST·DELETE /battles/match: 거리 오류 거부, 언랭은 배치고사 대결(waiting false, 판 번호·대결 ID),"
                    + " 대기 중 아니면 대기해제")
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
                .andExpect(status().isOk())
                // TC-5798: 언랭은 RANKED_ONLY 대신 배치고사 대결을 바로 만든다(05-03 P2, D-478·D-479)
                .andExpect(jsonPath("$.waiting").value(false))
                .andExpect(jsonPath("$.distanceMeters").value(5_000))
                .andExpect(jsonPath("$.defaultDistanceMeters").value(5_000))
                .andExpect(jsonPath("$.placementRound").value(1))
                .andExpect(jsonPath("$.battleId").isNumber());
        // 대기 중이 아닌 취소는 SRS 4.1 ELSE 갈래대로 거부한다(D-83 ABSENT, K-1)
        mvc.perform(as(s, delete("/api/v1/battles/match")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MATCHED"));
        mvc.perform(post("/api/v1/battles/match")).andExpect(status().isUnauthorized());
        mvc.perform(as(s, get("/api/v1/battles/match")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting").value(false))
                .andExpect(jsonPath("$.defaultDistanceMeters").value(5_000));
    }

    @Test
    @DisplayName(
            "TC-5799 배치고사 대결 결과·진행 중 대결은 placementRound를 싣고, 결과의 placementState는 판정 전이라 PENDING. 일반"
                    + " 대결은 둘 다 null")
    void result_carriesPlacementState() throws Exception {
        Session s = signUp();
        long battleId =
                body(mvc.perform(
                                        as(s, post("/api/v1/battles/match"))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(distance(3_000)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("battleId")
                        .asLong();
        mvc.perform(as(s, get("/api/v1/battles/" + battleId + "/result")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placementRound").value(1))
                .andExpect(jsonPath("$.placementState").value("PENDING"))
                .andExpect(jsonPath("$.type").value("RANDOM"));
        mvc.perform(as(s, get("/api/v1/battles/active")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.battleId").value(battleId))
                .andExpect(jsonPath("$.type").value("GHOST"))
                .andExpect(jsonPath("$.placementRound").value(1));
        mvc.perform(as(s, post("/api/v1/battles/" + battleId + "/forfeit")))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(as(s, get("/api/v1/battles/active"))).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /battles/match: 대기 상태와 마지막으로 요청한 대결 거리(기본 대결 거리, FR-080·FR-109)")
    void matchStatus_remembersLastDistance() throws Exception {
        Session s = signUp();
        for (int i = 0; i < 3; i++) {
            upload(s, 5_000, 1_500);
        }
        rank(s, Tier.GOLD);
        mvc.perform(
                        as(s, post("/api/v1/battles/match"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(distance(3_000)))
                .andExpect(jsonPath("$.defaultDistanceMeters").value(3_000));
        mvc.perform(as(s, get("/api/v1/battles/match")))
                .andExpect(jsonPath("$.waiting").value(true))
                .andExpect(jsonPath("$.distanceMeters").value(3_000))
                .andExpect(jsonPath("$.defaultDistanceMeters").value(3_000));
        mvc.perform(as(s, delete("/api/v1/battles/match")))
                .andExpect(jsonPath("$.waiting").value(false))
                .andExpect(jsonPath("$.defaultDistanceMeters").value(3_000));
    }

    @Test
    @DisplayName("랭크 회원은 대기 등록되고 두 번째 요청은 이미 대기 중, 취소하면 대기해제")
    void rankedMember_waitsAndCancels() throws Exception {
        Session s = signUp();
        for (int i = 0; i < 3; i++) {
            upload(s, 5_000, 1_500);
        }
        rank(s, Tier.GOLD);
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
                .andExpect(jsonPath("$.candidates.length()").value(5)) // 1km 추가(v0.14 D-238)
                .andExpect(jsonPath("$.candidates[0].distanceMeters").value(1_000))
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
        // 2026-10-30 결정: 셀프런 진행 중 포기(그만두기)는 포기자 패로 결과·이력에 남는다(TC-9310)
        mvc.perform(as(s, get("/api/v1/battles/" + battleId + "/result")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SELF"))
                .andExpect(jsonPath("$.verdict").value("LOSE"))
                .andExpect(jsonPath("$.rewardPoints").value(0))
                // 상대 캐릭터 성별(2026-10-31 결정): 항목은 늘 있고 과거의 나는 정하지 않아 null
                .andExpect(jsonPath("$").value(hasKey("opponentGender")))
                .andExpect(jsonPath("$.opponentGender").value(nullValue()));
        mvc.perform(as(s, get("/api/v1/battles/history").param("type", "SELF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.losses").value(1));
        mvc.perform(as(s, get("/api/v1/battles/history").param("size", "1")))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mvc.perform(as(s, get("/api/v1/battles/history").param("size", "0")))
                .andExpect(status().isBadRequest());
        // P1: 쿼리 이름(type·from·to·cursor·size)은 요청 record로 묶은 뒤에도 같다
        mvc.perform(
                        as(
                                s,
                                get("/api/v1/battles/history")
                                        .param("from", "2000-01-01")
                                        .param("to", "2000-01-02")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(as(s, get("/api/v1/battles/history").param("type", "NOPE")))
                .andExpect(status().isBadRequest());

        Session other = signUp();
        mvc.perform(as(other, get("/api/v1/battles/" + battleId + "/result")))
                .andExpect(status().isForbidden());
        mvc.perform(as(other, post("/api/v1/battles/" + battleId + "/cancel")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
            "GET /battles/active: 없으면 204, 진행 중이면 200(시작 보고 전 startAt null, 뒤에는 오프셋 없는 서버 시각), 포기하면"
                    + " 204이고 새 매칭을 받는다. 시작 보고 전 포기는 취소라 결과·이력이 없다(배틀런 QA 7번)")
    void activeBattle_andForfeitReleasesLock() throws Exception {
        Session s = signUp();
        mvc.perform(as(s, get("/api/v1/battles/active")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(get("/api/v1/battles/active")).andExpect(status().isUnauthorized());
        for (int i = 0; i < 3; i++) {
            upload(s, 5_000, 1_500);
        }
        rank(s, Tier.GOLD);
        long battleId =
                body(mvc.perform(
                                        as(s, post("/api/v1/battles/self"))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(distance(5_000)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("battleId")
                        .asLong();

        mvc.perform(as(s, get("/api/v1/battles/active")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.battleId").value(battleId))
                .andExpect(jsonPath("$.type").value("SELF"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.distanceMeters").value(5_000))
                .andExpect(jsonPath("$.opponentName").value("10/20의 나"))
                .andExpect(jsonPath("$").value(hasKey("opponentGender")))
                .andExpect(jsonPath("$.opponentGender").value(nullValue()))
                .andExpect(jsonPath("$.startAt").value(nullValue()));
        mvc.perform(
                        as(s, post("/api/v1/battles/match"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(distance(5_000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_IN_BATTLE"));
        mvc.perform(as(s, post("/api/v1/battles/" + battleId + "/started")))
                .andExpect(status().isNoContent());
        mvc.perform(as(s, get("/api/v1/battles/active")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.startAt")
                                .value(
                                        matchesPattern(
                                                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?")));

        mvc.perform(as(s, post("/api/v1/battles/" + battleId + "/forfeit")))
                .andExpect(status().isNoContent());
        mvc.perform(as(s, get("/api/v1/battles/active"))).andExpect(status().isNoContent());

        // 시작 보고 전 포기는 승패 없이 취소: 결과는 대결 없음, 이력에 나오지 않는다
        long unstarted =
                body(mvc.perform(
                                        as(s, post("/api/v1/battles/self"))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(distance(5_000)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("battleId")
                        .asLong();
        mvc.perform(as(s, post("/api/v1/battles/" + unstarted + "/forfeit")))
                .andExpect(status().isNoContent());
        mvc.perform(as(s, get("/api/v1/battles/" + unstarted + "/result")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BATTLE_NOT_FOUND"));
        // 진행 중 셀프런 포기는 패로 이력에 남고(2026-10-30 결정), 출발 전 포기는 남지 않는다
        mvc.perform(as(s, get("/api/v1/battles/history"))).andExpect(jsonPath("$.total").value(1));
        mvc.perform(as(s, get("/api/v1/battles/active"))).andExpect(status().isNoContent());

        // 취소(2026-10-30 결정): 진행 중 셀프런을 승패·이력 없이 지우고 활성 대결을 비운다
        long cancelled =
                body(mvc.perform(
                                        as(s, post("/api/v1/battles/self"))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(distance(5_000)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("battleId")
                        .asLong();
        mvc.perform(as(s, post("/api/v1/battles/" + cancelled + "/started")))
                .andExpect(status().isNoContent());
        mvc.perform(as(s, post("/api/v1/battles/" + cancelled + "/cancel")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(as(s, get("/api/v1/battles/active"))).andExpect(status().isNoContent());
        mvc.perform(as(s, get("/api/v1/battles/" + cancelled + "/result")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BATTLE_NOT_FOUND"));
        mvc.perform(as(s, post("/api/v1/battles/" + cancelled + "/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BATTLE_NOT_FOUND"));
        mvc.perform(as(s, get("/api/v1/battles/history"))).andExpect(jsonPath("$.total").value(1));
        // 끝난 대결(패로 판정된 셀프런)은 취소할 수 없다
        mvc.perform(as(s, post("/api/v1/battles/" + battleId + "/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mvc.perform(
                        as(s, post("/api/v1/battles/match"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(distance(5_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting").value(true));
        mvc.perform(as(s, delete("/api/v1/battles/match"))).andExpect(status().isOk());
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
