package com.a207.runkk.web.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.battle.CelebrityInput;
import com.a207.runkk.battle.CelebrityRecordService;
import com.a207.runkk.battle.TrackSample;
import com.a207.runkk.common.Distance;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** GET /battles/record-tracks?kind=(D-377, FR-260, 워치 상대 기록 미리 받기). 인증 필터를 거친다. */
@DisplayName("web.battle GET /battles/record-tracks")
class RecordTracksControllerTest extends WebTestSupport {

    private static final String PATH = "/api/v1/battles/record-tracks";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 20, 7, 0);
    private static final String OPERATOR = "record-tracks-test";

    @Autowired CelebrityRecordService celebrityRecordService;
    @Autowired JdbcTemplate jdbc;

    private static String at(LocalDateTime t) {
        return t.atZone(SEOUL).toOffsetDateTime().toString();
    }

    private String upload(Session s, long meters, int seconds) throws Exception {
        List<LocationPoint> points =
                RunFixtures.straightLine(START, (double) meters / seconds, seconds);
        Map<String, Object> body = new HashMap<>();
        String recordId = UUID.randomUUID().toString();
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

    private JsonNode tracks(Session s, String kind) throws Exception {
        return body(mvc.perform(as(s, get(PATH).param("kind", kind)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("tracks");
    }

    private static JsonNode byCelebrity(JsonNode tracks, long celebrityId) {
        for (JsonNode t : tracks) {
            if (!t.get("celebrityId").isNull() && t.get("celebrityId").asLong() == celebrityId) {
                return t;
            }
        }
        throw new AssertionError("셀럽 기록이 없다: " + celebrityId);
    }

    private static void assertSorted(JsonNode tracks) {
        for (int i = 1; i < tracks.size(); i++) {
            JsonNode before = tracks.get(i - 1);
            JsonNode after = tracks.get(i);
            long d0 = before.get("distanceMeters").asLong();
            long d1 = after.get("distanceMeters").asLong();
            assertThat(d0).isLessThanOrEqualTo(d1);
            if (d0 == d1) {
                assertThat(before.get("finishSeconds").asLong())
                        .isLessThanOrEqualTo(after.get("finishSeconds").asLong());
            }
        }
    }

    @Test
    @DisplayName("SELF: 기록 없으면 빈 목록, 5km를 뛰면 1·3·5km만(10km·하프 제외) 거리순, 대결을 만들지 않는다")
    void self_onlyDistancesWithRecord() throws Exception {
        Session s = signUp();
        assertThat(tracks(s, "SELF")).isEmpty();

        upload(s, 5_000, 1_500);
        JsonNode tracks = tracks(s, "SELF");

        assertThat(tracks).hasSize(3);
        assertThat(tracks.get(0).get("distanceMeters").asLong()).isEqualTo(1_000);
        assertThat(tracks.get(1).get("distanceMeters").asLong()).isEqualTo(3_000);
        JsonNode five = tracks.get(2);
        assertThat(five.get("distanceMeters").asLong()).isEqualTo(5_000);
        assertThat(five.get("finishSeconds").asLong()).isEqualTo(1_500);
        assertThat(five.get("opponentName").asText()).isEqualTo("10/20의 나");
        assertThat(five.get("celebrityId").isNull()).isTrue();
        assertThat(five.get("sourceLabel").isNull()).isTrue();
        assertThat(five.get("celebrityFigure").isNull()).isTrue();
        assertThat(five.get("estimated").asBoolean()).isFalse();
        JsonNode track = five.get("track");
        assertThat(track.get(0).get("elapsedSeconds").asLong()).isZero();
        assertThat(track.get(0).get("distanceMeters").asLong()).isZero();
        JsonNode last = track.get(track.size() - 1);
        assertThat(last.get("distanceMeters").asLong()).isEqualTo(5_000);
        assertThat(last.get("elapsedSeconds").asLong()).isEqualTo(1_500);
        assertSorted(tracks);

        mvc.perform(as(s, get("/api/v1/battles/active"))).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("SELF: track은 POST /battles/self가 주는 것과 같다")
    void self_trackEqualsStartSelfBattle() throws Exception {
        Session s = signUp();
        upload(s, 3_000, 900);
        JsonNode three = tracks(s, "SELF").get(1);

        JsonNode started =
                body(
                        mvc.perform(
                                        as(s, post("/api/v1/battles/self"))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(write(Map.of("distanceMeters", 3_000))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());

        assertThat(three.get("track")).isEqualTo(started.get("track"));
        assertThat(three.get("opponentName")).isEqualTo(started.get("opponentName"));
        mvc.perform(as(s, post("/api/v1/battles/" + started.get("battleId").asLong() + "/cancel")))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("SELF: 경로가 파기된 최고 기록의 거리는 빼고 나머지는 200으로 준다(읽기 트랜잭션이 깨지지 않는다)")
    void self_skipsPurgedRoute() throws Exception {
        Session s = signUp();
        String fast = upload(s, 3_000, 900); // 1·3km 최고 기록
        upload(s, 5_000, 1_800); // 더 느려 5km 최고 기록만
        jdbc.update(
                "update run_record set route_data = null, route_purged = true where record_id = ?",
                fast);

        JsonNode tracks = tracks(s, "SELF");

        assertThat(tracks).hasSize(1);
        assertThat(tracks.get(0).get("distanceMeters").asLong()).isEqualTo(5_000);
    }

    @Test
    @DisplayName("CELEBRITY: 등록된 기록 전부, 거리 → 완주 시간순, 구간 없으면 균등 페이스 estimated")
    void celebrity_allSortedAndEstimated() throws Exception {
        Distance fiveK = Distance.ofMeters(5_000);
        List<Long> ids = new ArrayList<>();
        try {
            long uniform =
                    register(
                            new CelebrityInput(
                                    "균등전설",
                                    fiveK,
                                    Duration.ofSeconds(900),
                                    List.of(),
                                    "대회 공식 기록",
                                    null,
                                    null,
                                    "legend"));
            ids.add(uniform);
            long split =
                    register(
                            new CelebrityInput(
                                    "구간전설",
                                    fiveK,
                                    Duration.ofSeconds(960),
                                    List.of(
                                            new TrackSample(
                                                    Duration.ofSeconds(480),
                                                    Distance.ofMeters(2_500)),
                                            new TrackSample(Duration.ofSeconds(960), fiveK)),
                                    "대회 공식 기록",
                                    null));
            ids.add(split);

            Session s = signUp();
            JsonNode tracks = tracks(s, "CELEBRITY");

            assertSorted(tracks);
            JsonNode estimated = byCelebrity(tracks, uniform);
            assertThat(estimated.get("estimated").asBoolean()).isTrue();
            assertThat(estimated.get("opponentName").asText()).isEqualTo("균등전설");
            assertThat(estimated.get("finishSeconds").asLong()).isEqualTo(900);
            assertThat(estimated.get("sourceLabel").asText()).isEqualTo("출처: 대회 공식 기록");
            assertThat(estimated.get("celebrityFigure").asText()).isEqualTo("legend");
            // 균등 페이스 1초 표본: (0,0) + 1..899초 + (900, 5000)
            JsonNode track = estimated.get("track");
            assertThat(track).hasSize(901);
            assertThat(track.get(450).get("distanceMeters").asLong()).isEqualTo(2_500);
            assertThat(track.get(900).get("distanceMeters").asLong()).isEqualTo(5_000);

            JsonNode withSplits = byCelebrity(tracks, split);
            assertThat(withSplits.get("estimated").asBoolean()).isFalse();
            assertThat(withSplits.get("celebrityFigure").isNull()).isTrue();
            assertThat(withSplits.get("track")).hasSize(3);
            assertThat(tracks.findValues("celebrityId").indexOf(estimated.get("celebrityId")))
                    .isLessThan(
                            tracks.findValues("celebrityId")
                                    .indexOf(withSplits.get("celebrityId")));
            mvc.perform(as(s, get("/api/v1/battles/active"))).andExpect(status().isNoContent());
        } finally {
            ids.forEach(id -> celebrityRecordService.delete(OPERATOR, id, "시험 정리"));
        }
    }

    private long register(CelebrityInput in) {
        return celebrityRecordService.register(OPERATOR, in).celebrityId();
    }

    @Test
    @DisplayName("게스트도 받는다")
    void guest_isAllowed() throws Exception {
        JsonNode guest =
                body(
                        mvc.perform(
                                        post("/api/v1/members/guest")
                                                .header("X-Real-IP", "10.78.0.1")
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
        mvc.perform(
                        get(PATH)
                                .param("kind", "SELF")
                                .header(
                                        "Authorization",
                                        "Bearer " + guest.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks").isArray());
    }

    @Test
    @DisplayName("kind가 RANDOM·GHOST·FRIEND·모르는 값·없음이면 400 BAD_REQUEST, 토큰 없으면 401")
    void badKind_andUnauthorized() throws Exception {
        Session s = signUp();
        for (String kind : List.of("RANDOM", "GHOST", "FRIEND", "self", "WHATEVER")) {
            mvc.perform(as(s, get(PATH).param("kind", kind)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        mvc.perform(as(s, get(PATH)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get(PATH).param("kind", "SELF")).andExpect(status().isUnauthorized());
    }
}
