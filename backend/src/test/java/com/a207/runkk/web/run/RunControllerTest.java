package com.a207.runkk.web.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.MotionSample;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunRecordRepository;
import com.a207.runkk.running.StartDevice;
import com.a207.runkk.web.WebTestSupport;
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

/** POST /runs, POST /runs/{id}/heart-rates, GET /me/disposals, POST /me/disposals/{id}/confirm. */
@DisplayName("web.run 러닝 업로드·늦은 심박·파기 지시")
class RunControllerTest extends WebTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired private RunRecordRepository runs;
    @Autowired private RunQueryService runQuery;
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 20, 7, 0);

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
        body.put(
                "heartRates", List.of(Map.of("measuredAt", at(START.plusSeconds(10)), "bpm", 150)));
        return body;
    }

    @Test
    @DisplayName("업로드는 처음 STORED, 같은 기록 ID는 ALREADY_STORED(둘 다 200)")
    void upload_isIdempotent() throws Exception {
        Session s = signUp();
        String id = UUID.randomUUID().toString();
        String body = write(run(id, 5_000, 1_500));
        mvc.perform(
                        as(s, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STORED"))
                .andExpect(jsonPath("$.valid").value(true));
        mvc.perform(
                        as(s, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_STORED"));
        mvc.perform(post("/api/v1/runs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("형식 오류(UUID 아님, 포인트 수 불일치, 좌표 범위 밖)는 400")
    void upload_rejectsMalformed() throws Exception {
        Session s = signUp();
        mvc.perform(
                        as(s, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(run("not-a-uuid", 5_000, 1_500))))
                .andExpect(status().isBadRequest());
        Map<String, Object> mismatch = run(UUID.randomUUID().toString(), 5_000, 1_500);
        mismatch.put("pointCount", 3);
        mvc.perform(
                        as(s, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(mismatch)))
                .andExpect(status().isBadRequest());
        Map<String, Object> badPoint = run(UUID.randomUUID().toString(), 1_000, 300);
        badPoint.put("pointCount", 1);
        badPoint.put(
                "points",
                List.of(
                        Map.of(
                                "measuredAt",
                                at(START),
                                "latitude",
                                99.0,
                                "longitude",
                                0.0,
                                "accuracyMeters",
                                5)));
        mvc.perform(
                        as(s, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(badPoint)))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        as(s, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("늦은 심박: 주인만(403), 없는 기록은 409")
    void lateHeartRates() throws Exception {
        Session owner = signUp();
        Session other = signUp();
        mvc.perform(as(owner, put("/api/v1/me/consents/HEART_RATE"))).andExpect(status().isOk());
        String id = UUID.randomUUID().toString();
        mvc.perform(
                        as(owner, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(run(id, 5_000, 1_500))))
                .andExpect(status().isOk());
        String late =
                write(
                        Map.of(
                                "samples",
                                List.of(
                                        Map.of(
                                                "measuredAt",
                                                at(START.plusSeconds(20)),
                                                "bpm",
                                                160))));
        mvc.perform(
                        as(owner, post("/api/v1/runs/" + id + "/heart-rates"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(late))
                .andExpect(status().isNoContent());
        mvc.perform(
                        as(other, post("/api/v1/runs/" + id + "/heart-rates"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(late))
                .andExpect(status().isForbidden());
        mvc.perform(
                        as(owner, post("/api/v1/runs/" + UUID.randomUUID() + "/heart-rates"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(late))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
    }

    @Test
    @DisplayName("파기 지시: 기기 확인 뒤 목록에서 빠지고, 남의 지시는 403")
    void disposals() throws Exception {
        Session s = signUp();
        Session other = signUp();
        mvc.perform(as(s, delete("/api/v1/me/consents/LOCATION"))).andExpect(status().isOk());
        long orderId =
                body(mvc.perform(as(s, get("/api/v1/me/disposals")))
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get(0)
                        .get("id")
                        .asLong();
        mvc.perform(as(other, post("/api/v1/me/disposals/" + orderId + "/confirm")))
                .andExpect(status().isForbidden());
        mvc.perform(as(s, post("/api/v1/me/disposals/" + orderId + "/confirm")))
                .andExpect(status().isNoContent());
        mvc.perform(as(s, get("/api/v1/me/disposals"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(s, post("/api/v1/me/disposals/999999/confirm")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPOSAL_ORDER_NOT_FOUND"));
    }

    private static Map<String, Object> pause(LocalDateTime from, LocalDateTime to) {
        return Map.of("from", at(from), "to", at(to));
    }

    private void expectStatus(Session s, Map<String, Object> body, int status) throws Exception {
        mvc.perform(
                        as(s, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(body)))
                .andExpect(status().is(status));
    }

    @Test
    @DisplayName("TC-6677: 정지 구간 규칙 위반 일곱 가지는 400(저장 0), 100개는 200(D-163 맞닿음 포함)")
    void upload_rejectsBadPauses() throws Exception {
        Session s = signUp();
        LocalDateTime a = START.plusMinutes(5);
        LocalDateTime b = START.plusMinutes(10);
        LocalDateTime c = START.plusMinutes(15);
        List<List<Map<String, Object>>> bad =
                List.of(
                        List.of(pause(a, a)),
                        List.of(pause(START.minusSeconds(1), b)),
                        List.of(pause(b, START.plusSeconds(1_501))),
                        List.of(pause(a, b), pause(b, c)),
                        List.of(pause(a, c), pause(b, c.plusMinutes(1))),
                        List.of(pause(b, c), pause(START.plusMinutes(1), a)));
        List<String> rejected = new java.util.ArrayList<>();
        for (List<Map<String, Object>> pauses : bad) {
            Map<String, Object> body = run(UUID.randomUUID().toString(), 5_000, 1_500);
            body.put("pauses", pauses);
            expectStatus(s, body, 400);
            rejected.add((String) body.get("recordId"));
        }
        Map<String, Object> battle = run(UUID.randomUUID().toString(), 5_000, 1_500);
        battle.put("mode", "BATTLE");
        battle.put("cardKind", "BATTLE_RUN");
        battle.put("battleId", 1);
        battle.put("pauses", List.of(pause(a, b)));
        expectStatus(s, battle, 400);

        List<Map<String, Object>> many = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            LocalDateTime from = START.plusSeconds(2L * i + 1);
            many.add(pause(from, from.plusSeconds(1)));
        }
        Map<String, Object> tooMany = run(UUID.randomUUID().toString(), 5_000, 1_500);
        tooMany.put("pauses", many);
        expectStatus(s, tooMany, 400);
        Map<String, Object> hundred = run(UUID.randomUUID().toString(), 5_000, 1_500);
        hundred.put("pauses", many.subList(0, 100));
        expectStatus(s, hundred, 200);

        for (String id : rejected) {
            mvc.perform(as(s, get("/api/v1/runs/" + id)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("W23: startDevice(PHONE·WATCH)는 선택 항목이고 없거나 null이면 PHONE으로 저장한다, 모르는 값은 400")
    void upload_startDevice() throws Exception {
        Session s = signUp();
        String watchId = UUID.randomUUID().toString();
        Map<String, Object> watch = run(watchId, 5_000, 1_500);
        watch.put("startDevice", "WATCH");
        expectStatus(s, watch, 200);
        String oldAppId = UUID.randomUUID().toString();
        expectStatus(s, run(oldAppId, 5_000, 1_500), 200);
        String nullId = UUID.randomUUID().toString();
        Map<String, Object> nullDevice = run(nullId, 5_000, 1_500);
        nullDevice.put("startDevice", null);
        expectStatus(s, nullDevice, 200);

        assertThat(runs.findById(watchId).orElseThrow().getStartDevice())
                .isEqualTo(StartDevice.WATCH);
        assertThat(runs.findById(oldAppId).orElseThrow().getStartDevice())
                .isEqualTo(StartDevice.PHONE);
        assertThat(runs.findById(nullId).orElseThrow().getStartDevice())
                .isEqualTo(StartDevice.PHONE);

        Map<String, Object> unknown = run(UUID.randomUUID().toString(), 5_000, 1_500);
        unknown.put("startDevice", "TABLET");
        expectStatus(s, unknown, 400);
    }

    @Test
    @DisplayName("S15P31A207-242: motions(속도·케이던스)는 선택 항목이다. 옛 앱 형식은 빈 목록, 값 없는·음수 값은 버린다")
    void upload_motionsAreOptional() throws Exception {
        Session s = signUp();
        String oldAppId = UUID.randomUUID().toString();
        expectStatus(s, run(oldAppId, 5_000, 1_500), 200);
        String nullId = UUID.randomUUID().toString();
        Map<String, Object> nullMotions = run(nullId, 5_000, 1_500);
        nullMotions.put("motions", null);
        expectStatus(s, nullMotions, 200);

        String newId = UUID.randomUUID().toString();
        Map<String, Object> withMotions = run(newId, 5_000, 1_500);
        Map<String, Object> empty = new HashMap<>();
        empty.put("measuredAt", at(START.plusSeconds(3)));
        empty.put("speedMps", null);
        withMotions.put(
                "motions",
                List.of(
                        Map.of("measuredAt", at(START.plusSeconds(1)), "speedMps", 3.4),
                        Map.of("measuredAt", at(START.plusSeconds(2)), "cadenceSpm", 172),
                        Map.of(
                                "measuredAt",
                                at(START.plusSeconds(4)),
                                "speedMps",
                                -1,
                                "cadenceSpm",
                                170),
                        empty));
        expectStatus(s, withMotions, 200);

        assertThat(runQuery.bodyInputOf(oldAppId).orElseThrow().motions()).isEmpty();
        assertThat(runQuery.bodyInputOf(nullId).orElseThrow().motions()).isEmpty();
        assertThat(runQuery.bodyInputOf(newId).orElseThrow().motions())
                .containsExactly(
                        new MotionSample(START.plusSeconds(1), 3.4, null),
                        new MotionSample(START.plusSeconds(2), null, 172.0),
                        new MotionSample(START.plusSeconds(4), null, 170.0));

        Map<String, Object> noTime = run(UUID.randomUUID().toString(), 5_000, 1_500);
        noTime.put("motions", List.of(Map.of("speedMps", 3.0)));
        expectStatus(s, noTime, 400);
    }

    @Test
    @DisplayName("TC-6685: pauses 항목이 없거나 null·빈 목록이면 정지 없음으로 저장한다")
    void upload_withoutPausesIsCompatible() throws Exception {
        Session s = signUp();
        expectStatus(s, run(UUID.randomUUID().toString(), 5_000, 1_500), 200);
        Map<String, Object> nullPauses = run(UUID.randomUUID().toString(), 5_000, 1_500);
        nullPauses.put("pauses", null);
        expectStatus(s, nullPauses, 200);
        Map<String, Object> empty = run(UUID.randomUUID().toString(), 5_000, 1_500);
        empty.put("pauses", List.of());
        mvc.perform(
                        as(s, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(empty)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }
}
