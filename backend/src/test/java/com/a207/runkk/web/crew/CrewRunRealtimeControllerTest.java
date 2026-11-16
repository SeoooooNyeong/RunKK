package com.a207.runkk.web.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.crew.CrewRunService;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 크루런 실시간 반영(설계 05-32 5절 API 계약, S15P31A207-306): 안전한 대기실 GET, 멱등 준비 PUT, 모집자 지금 시작, 중간 참여, 날짜순 진행
 * 목록. 인증 필터를 거치고 test 프로필(H2 + 내장 Redis)에서 돈다.
 */
@DisplayName("web.crew 크루런 실시간 반영 엔드포인트(05-32)")
class CrewRunRealtimeControllerTest extends WebTestSupport {

    private static final AtomicInteger CREWS = new AtomicInteger();
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired CrewRunService crewRunService;

    private ResultActions call(Session s, MockHttpServletRequestBuilder req, Object body)
            throws Exception {
        MockHttpServletRequestBuilder r = as(s, req);
        if (body != null) {
            r = r.contentType(MediaType.APPLICATION_JSON).content(write(body));
        }
        return mvc.perform(r);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return body(result.andReturn().getResponse().getContentAsString());
    }

    private long crewOf(Session leader, Session... members) throws Exception {
        Map<String, Object> form = new HashMap<>();
        form.put("name", "실시간" + CREWS.incrementAndGet() + "크루");
        form.put("intro", "함께 달려요");
        form.put("tags", List.of("WEEKDAY_EVENING"));
        form.put("visibility", "PUBLIC");
        form.put("joinPolicy", "FREE");
        long crewId =
                json(call(leader, post("/api/v1/crews"), form).andExpect(status().isCreated()))
                        .get("id")
                        .asLong();
        for (Session m : members) {
            call(m, post("/api/v1/crews/join"), Map.of("crewId", crewId))
                    .andExpect(jsonPath("$.status").value("JOINED"));
        }
        return crewId;
    }

    private long crewRun(Session author, long crewId, LocalDateTime at, String title)
            throws Exception {
        Map<String, Object> form = new HashMap<>();
        form.put("kind", "CREW_RUN");
        form.put("title", title);
        form.put("body", "같이 달려요");
        form.put("distanceMeters", 3000);
        form.put("scheduledAt", at.atZone(SEOUL).toOffsetDateTime().toString());
        return json(call(author, post("/api/v1/crews/" + crewId + "/posts"), form)
                        .andExpect(status().isCreated()))
                .get("id")
                .asLong();
    }

    /** 20분 안쪽의 5분 단위 시각(W6 5분 단위, 대기실은 30분 전에 열린다). */
    private static LocalDateTime slotIn(int minutes) {
        LocalDateTime t = LocalDateTime.now().plusMinutes(minutes).truncatedTo(ChronoUnit.MINUTES);
        return t.withMinute(t.getMinute() / 5 * 5);
    }

    private JsonNode lobby(Session s, long run) throws Exception {
        return json(
                call(s, get("/api/v1/crew-runs/" + run + "/lobby"), null)
                        .andExpect(status().isOk()));
    }

    private static String readinessOf(JsonNode lobby, long memberId) {
        for (JsonNode p : lobby.get("participants")) {
            if (p.get("memberId").asLong() == memberId) {
                return p.get("readiness").asText();
            }
        }
        return null;
    }

    @Test
    @DisplayName(
            "[임시:TC-성-11019] READY 참가자가 대기실 GET을 10번 읽어도 준비·참가·revision이 그대로다(enter 재호출과 달리 읽기 전용)")
    void lobbyGet_doesNotResetReady() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        long crewId = crewOf(leader, a);
        long run = crewRun(leader, crewId, slotIn(20), "읽기만");
        call(leader, post("/api/v1/crew-runs/" + run + "/lobby/enter"), null)
                .andExpect(status().isOk());
        call(a, post("/api/v1/crew-runs/" + run + "/lobby/enter"), null).andExpect(status().isOk());
        call(a, put("/api/v1/crew-runs/" + run + "/lobby/ready"), Map.of("ready", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("LOBBY"));

        JsonNode first = lobby(a, run);
        long revision = first.get("revision").asLong();
        for (int i = 0; i < 10; i++) {
            JsonNode again = lobby(a, run);
            assertThat(readinessOf(again, a.memberId())).isEqualTo("READY");
            assertThat(readinessOf(again, leader.memberId())).isEqualTo("ENTERING");
            assertThat(again.get("revision").asLong()).isEqualTo(revision);
            assertThat(again.get("phase").asText()).isEqualTo("LOBBY");
        }
        assertThat(first.get("crewId").asLong()).isEqualTo(crewId);
        assertThat(first.get("canJoin").asBoolean()).isFalse();
        assertThat(first.get("meRunner").asBoolean()).isFalse();
        assertThat(first.get("countdownParticipantIds")).isEmpty();
        assertThat(first.get("countdownToken").isNull()).isTrue();
        assertThat(first.get("startAt").isNull()).isTrue();
        assertThat(first.get("measurementRecordId").isNull()).isTrue();
    }

    @Test
    @DisplayName(
            "멱등 준비 PUT: 같은 값은 revision을 올리지 않고, 입장하지 않았으면 409 NOT_PARTICIPANT, 형식 오류는 400(05-32"
                    + " 5.1)")
    void readyPut_isIdempotent() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        Session b = signUp();
        long crewId = crewOf(leader, a, b);
        long run = crewRun(leader, crewId, slotIn(20), "멱등 준비");
        for (Session s : List.of(leader, a, b)) {
            call(s, post("/api/v1/crew-runs/" + run + "/lobby/enter"), null)
                    .andExpect(status().isOk());
        }
        JsonNode once =
                json(
                        call(
                                        a,
                                        put("/api/v1/crew-runs/" + run + "/lobby/ready"),
                                        Map.of("ready", true))
                                .andExpect(status().isOk()));
        JsonNode twice =
                json(
                        call(
                                        a,
                                        put("/api/v1/crew-runs/" + run + "/lobby/ready"),
                                        Map.of("ready", true))
                                .andExpect(status().isOk()));
        assertThat(readinessOf(twice, a.memberId())).isEqualTo("READY");
        assertThat(twice.get("revision").asLong()).isEqualTo(once.get("revision").asLong());
        JsonNode cancelled =
                json(
                        call(
                                        a,
                                        put("/api/v1/crew-runs/" + run + "/lobby/ready"),
                                        Map.of("ready", false))
                                .andExpect(status().isOk()));
        assertThat(readinessOf(cancelled, a.memberId())).isEqualTo("ENTERING");
        assertThat(cancelled.get("revision").asLong()).isGreaterThan(once.get("revision").asLong());

        Session outsider = signUp();
        call(outsider, put("/api/v1/crew-runs/" + run + "/lobby/ready"), Map.of("ready", true))
                .andExpect(status().isForbidden());
        call(leader, post("/api/v1/crew-runs/" + run + "/lobby/leave"), null)
                .andExpect(status().isOk());
        call(leader, put("/api/v1/crew-runs/" + run + "/lobby/ready"), Map.of("ready", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_PARTICIPANT"));
        call(a, put("/api/v1/crew-runs/" + run + "/lobby/ready"), Map.of())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
            "[임시:TC-성-11025] 모집자만 지금 시작(비모집자 403), 준비 1명이면 409 CREW_RUN_NOT_READY, 준비 2명이면 후보 고정"
                    + " + startAt=예약+3초, 재요청은 같은 token")
    void startNow_hostOnlyAndIdempotent() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        Session b = signUp();
        long crewId = crewOf(leader, a, b);
        long run = crewRun(leader, crewId, slotIn(20), "지금 시작");
        for (Session s : List.of(leader, a, b)) {
            call(s, post("/api/v1/crew-runs/" + run + "/lobby/enter"), null)
                    .andExpect(status().isOk());
        }
        call(leader, put("/api/v1/crew-runs/" + run + "/lobby/ready"), Map.of("ready", true))
                .andExpect(jsonPath("$.canStartNow").value(false));
        call(leader, post("/api/v1/crew-runs/" + run + "/start"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREW_RUN_NOT_READY"));
        call(a, put("/api/v1/crew-runs/" + run + "/lobby/ready"), Map.of("ready", true))
                .andExpect(jsonPath("$.phase").value("LOBBY"))
                .andExpect(jsonPath("$.canStartNow").value(false));
        assertThat(lobby(leader, run).get("canStartNow").asBoolean()).isTrue();
        call(a, post("/api/v1/crew-runs/" + run + "/start"), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        LocalDateTime before = LocalDateTime.now().withNano(0);
        JsonNode started =
                json(
                        call(leader, post("/api/v1/crew-runs/" + run + "/start"), null)
                                .andExpect(status().isOk()));
        assertThat(started.get("phase").asText()).isEqualTo("COUNTDOWN");
        assertThat(started.get("status").asText()).isEqualTo("LOBBY_OPEN");
        assertThat(started.get("canStartNow").asBoolean()).isFalse();
        String token = started.get("countdownToken").asText();
        LocalDateTime startAt = localOf(started.get("startAt"));
        assertThat(startAt).isBetween(before.plusSeconds(3), LocalDateTime.now().plusSeconds(4));
        assertThat(ids(started.get("countdownParticipantIds")))
                .containsExactlyInAnyOrder(leader.memberId(), a.memberId());

        JsonNode again =
                json(
                        call(leader, post("/api/v1/crew-runs/" + run + "/start"), null)
                                .andExpect(status().isOk()));
        assertThat(again.get("countdownToken").asText()).isEqualTo(token);
        assertThat(localOf(again.get("startAt"))).isEqualTo(startAt);
    }

    @Test
    @DisplayName(
            "[임시:TC-성-11028·11029] 출발 뒤 크루원 누구나 join, 승인 시각과 측정 기록 ID는 10번 다시 불러도 같다. 출발 전·종료 뒤는"
                    + " 409 INVALID_STATE, 비크루원은 403")
    void join_afterDeparture() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        Session c = signUp();
        long crewId = crewOf(leader, a, c);
        LocalDateTime at = slotIn(20);
        long run = crewRun(leader, crewId, at, "중간 참여");
        call(c, post("/api/v1/crew-runs/" + run + "/join"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        for (Session s : List.of(leader, a)) {
            call(s, post("/api/v1/crew-runs/" + run + "/lobby/enter"), null)
                    .andExpect(status().isOk());
            call(s, put("/api/v1/crew-runs/" + run + "/lobby/ready"), Map.of("ready", true))
                    .andExpect(status().isOk());
        }
        JsonNode countdown = lobby(a, run);
        assertThat(countdown.get("phase").asText()).isEqualTo("COUNTDOWN");
        LocalDateTime startAt = localOf(countdown.get("startAt"));
        crewRunService.tick(startAt);
        JsonNode running = lobby(a, run);
        assertThat(running.get("phase").asText()).isEqualTo("RUNNING");
        assertThat(running.get("meRunner").asBoolean()).isTrue();
        assertThat(running.get("measurementRecordId").asText()).hasSize(36);
        assertThat(localOf(running.get("startAt"))).isEqualTo(startAt);
        assertThat(lobby(c, run).get("canJoin").asBoolean()).isTrue();

        JsonNode joined =
                json(
                        call(c, post("/api/v1/crew-runs/" + run + "/join"), null)
                                .andExpect(status().isOk()));
        assertThat(joined.get("alreadyJoined").asBoolean()).isFalse();
        assertThat(joined.get("crewId").asLong()).isEqualTo(crewId);
        String recordId = joined.get("measurementRecordId").asText();
        String joinedAt = joined.get("joinedAt").asText();
        assertThat(recordId).hasSize(36);
        assertThat(localOf(joined.get("joinedAt"))).isAfterOrEqualTo(startAt.minusSeconds(4));
        List<Long> runners = new ArrayList<>();
        joined.get("participants").forEach(p -> runners.add(p.get("memberId").asLong()));
        assertThat(runners).contains(leader.memberId(), a.memberId(), c.memberId());
        for (int i = 0; i < 10; i++) {
            JsonNode retry =
                    json(
                            call(c, post("/api/v1/crew-runs/" + run + "/join"), null)
                                    .andExpect(status().isOk()));
            assertThat(retry.get("alreadyJoined").asBoolean()).isTrue();
            assertThat(retry.get("measurementRecordId").asText()).isEqualTo(recordId);
            assertThat(retry.get("joinedAt").asText()).isEqualTo(joinedAt);
        }
        JsonNode cLobby = lobby(c, run);
        assertThat(cLobby.get("meRunner").asBoolean()).isTrue();
        assertThat(cLobby.get("canJoin").asBoolean()).isFalse();
        assertThat(cLobby.get("measurementRecordId").asText()).isEqualTo(recordId);
        assertThat(ids(cLobby.get("countdownParticipantIds"))).doesNotContain(c.memberId());

        Session outsider = signUp();
        call(outsider, post("/api/v1/crew-runs/" + run + "/join"), null)
                .andExpect(status().isForbidden());
        call(outsider, get("/api/v1/crew-runs/" + run + "/lobby"), null)
                .andExpect(status().isForbidden());

        crewRunService.tick(startAt.plusHours(3));
        Session late = signUp();
        call(late, post("/api/v1/crews/join"), Map.of("crewId", crewId));
        call(late, post("/api/v1/crew-runs/" + run + "/join"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        assertThat(lobby(leader, run).get("phase").asText()).isEqualTo("FINISHED");
    }

    @Test
    @DisplayName(
            "[임시:TC-성-11033] 진행 크루런 날짜순 목록: (예정일시, ID) 오름차순, 같은 일시는 ID순, 페이지 중복 0, 지운 기준 행 cursor는"
                    + " 이어 읽고 없는 cursor·잘못된 order는 400")
    void scheduledAscendingList() throws Exception {
        Session leader = signUp();
        long crewId = crewOf(leader);
        LocalDateTime late = slotIn(60 * 24 * 3);
        LocalDateTime early = slotIn(60 * 24);
        long r1 = crewRun(leader, crewId, late, "늦은 회차");
        long r2 = crewRun(leader, crewId, early, "이른 회차 1");
        long r3 = crewRun(leader, crewId, early, "이른 회차 2");
        long gone = crewRun(leader, crewId, slotIn(60 * 24 * 2), "지울 회차");
        call(
                        leader,
                        post("/api/v1/crews/" + crewId + "/posts"),
                        Map.of("kind", "CHEER", "body", "응원"))
                .andExpect(status().isCreated());

        List<Long> seen = new ArrayList<>();
        Long cursor = null;
        for (int page = 0; page < 10; page++) {
            MockHttpServletRequestBuilder req =
                    get("/api/v1/crews/" + crewId + "/posts")
                            .param("kinds", "CREW_RUN")
                            .param("order", "SCHEDULED_ASC")
                            .param("activeOnly", "true")
                            .param("size", "1");
            if (cursor != null) {
                req = req.param("cursor", String.valueOf(cursor));
            }
            JsonNode body = json(call(leader, req, null).andExpect(status().isOk()));
            body.get("posts").forEach(p -> seen.add(p.get("postId").asLong()));
            if (body.get("nextCursor").isNull()) {
                break;
            }
            cursor = body.get("nextCursor").asLong();
            if (seen.size() == 3) {
                call(leader, delete("/api/v1/posts/" + gone), null)
                        .andExpect(status().isNoContent());
            }
        }
        assertThat(seen).containsExactly(r2, r3, gone, r1);

        call(
                        leader,
                        get("/api/v1/crews/" + crewId + "/posts")
                                .param("kinds", "CREW_RUN")
                                .param("order", "SCHEDULED_ASC")
                                .param("cursor", "999999999"),
                        null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        call(
                        leader,
                        get("/api/v1/crews/" + crewId + "/posts")
                                .param("kinds", "CREW_RUN")
                                .param("order", "NOPE"),
                        null)
                .andExpect(status().isBadRequest());
        call(
                        leader,
                        get("/api/v1/crews/" + crewId + "/posts")
                                .param("kinds", "CHEER")
                                .param("order", "SCHEDULED_ASC"),
                        null)
                .andExpect(status().isBadRequest());
        JsonNode newest = json(call(leader, get("/api/v1/crews/" + crewId + "/posts"), null));
        assertThat(newest.get("posts").get(0).get("kind").asText())
                .as("기본 순서(ID 내림차순)는 그대로")
                .isEqualTo("CHEER");
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asLong()));
        return out;
    }

    private static LocalDateTime localOf(JsonNode text) {
        return OffsetDateTime.parse(text.asText()).atZoneSameInstant(SEOUL).toLocalDateTime();
    }
}
