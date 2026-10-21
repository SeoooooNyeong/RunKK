package com.a207.runkk.web.run;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.web.WebTestSupport;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** POST /runs, POST /runs/{id}/heart-rates, GET /me/disposals, POST /me/disposals/{id}/confirm. */
@DisplayName("web.run 러닝 업로드·늦은 심박·파기 지시")
class RunControllerTest extends WebTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
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
}
