package com.a207.runkk.web.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 피그마 맞춤 W5(S15P31A207-296) 채팅 계약: 작성자 직책 authorRole, 메시지 종류 messageKind, 반응 CHECK, 반응한 사람 목록 GET
 * /chat-messages/{id}/reactions, 러닝 완료 시스템 메시지(contract-W5.md). test 프로필(H2 + 내장 Redis).
 */
@DisplayName("web.chat 피그마 W5 채팅 계약")
class ChatFigmaW5ControllerTest extends WebTestSupport {

    private static final AtomicInteger CREWS = new AtomicInteger();
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired JdbcTemplate jdbc;

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
        form.put("name", "더블유파이브" + CREWS.incrementAndGet());
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

    private long say(Session s, long crewId, String content) throws Exception {
        return json(call(
                                s,
                                post("/api/v1/crews/" + crewId + "/chat/messages"),
                                Map.of(
                                        "clientMessageId",
                                        UUID.randomUUID().toString(),
                                        "content",
                                        content))
                        .andExpect(status().isCreated()))
                .get("messageId")
                .asLong();
    }

    private JsonNode history(Session s, long crewId) throws Exception {
        return json(call(s, get("/api/v1/crews/" + crewId + "/chat/messages"), null)
                        .andExpect(status().isOk()))
                .get("messages");
    }

    @Test
    @DisplayName(
            "메시지에 messageKind TEXT·authorRole(크루장 LEADER, 크루원 MEMBER)·runDistanceMeters null이 실린다")
    void authorRoleAndKind() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        long crewId = crewOf(leader, a);

        JsonNode posted =
                json(
                        call(
                                        leader,
                                        post("/api/v1/crews/" + crewId + "/chat/messages"),
                                        Map.of(
                                                "clientMessageId",
                                                UUID.randomUUID().toString(),
                                                "content",
                                                "공지"))
                                .andExpect(status().isCreated()));
        assertThat(posted.get("messageKind").asText()).isEqualTo("TEXT");
        assertThat(posted.get("authorRole").asText()).isEqualTo("LEADER");
        assertThat(posted.has("runDistanceMeters")).isTrue();
        assertThat(posted.get("runDistanceMeters").isNull()).isTrue();
        say(a, crewId, "네");

        JsonNode lines = history(a, crewId);
        assertThat(lines.get(0).get("authorRole").asText()).isEqualTo("LEADER");
        assertThat(lines.get(1).get("authorRole").asText()).isEqualTo("MEMBER");
        assertThat(lines.get(1).get("messageKind").asText()).isEqualTo("TEXT");
    }

    @Test
    @DisplayName("반응 CHECK를 달고, 반응한 사람 목록은 먼저 단 순서·kind 필터·me, 모르는 kind 400, 크루 밖 409, 없는 메시지 409")
    void reactors() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        Session outsider = signUp();
        long crewId = crewOf(leader, a);
        long id = say(leader, crewId, "출석 체크");

        call(a, put("/api/v1/chat-messages/" + id + "/reactions/CHECK"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions.CHECK").value(1))
                .andExpect(jsonPath("$.myReactions[0]").value("CHECK"));
        Thread.sleep(5);
        call(leader, put("/api/v1/chat-messages/" + id + "/reactions/LIKE"), null)
                .andExpect(status().isOk());

        JsonNode all =
                json(
                        call(a, get("/api/v1/chat-messages/" + id + "/reactions"), null)
                                .andExpect(status().isOk()));
        assertThat(all.get("messageId").asLong()).isEqualTo(id);
        JsonNode rows = all.get("reactors");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("memberId").asLong()).isEqualTo(a.memberId());
        assertThat(rows.get(0).get("kind").asText()).isEqualTo("CHECK");
        assertThat(rows.get(0).get("me").asBoolean()).isTrue();
        assertThat(rows.get(0).get("displayName").asText()).isNotBlank();
        assertThat(rows.get(0).get("reactedAt").asText()).endsWith("+09:00");
        assertThat(rows.get(1).get("memberId").asLong()).isEqualTo(leader.memberId());
        assertThat(rows.get(1).get("me").asBoolean()).isFalse();

        JsonNode likes =
                json(
                        call(a, get("/api/v1/chat-messages/" + id + "/reactions?kind=LIKE"), null)
                                .andExpect(status().isOk()));
        assertThat(likes.get("reactors")).hasSize(1);
        assertThat(likes.get("reactors").get(0).get("kind").asText()).isEqualTo("LIKE");

        call(a, get("/api/v1/chat-messages/" + id + "/reactions?kind=WOW"), null)
                .andExpect(status().isBadRequest());
        call(outsider, get("/api/v1/chat-messages/" + id + "/reactions"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CREW_MEMBER"));
        call(a, get("/api/v1/chat-messages/987654321/reactions"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_MESSAGE_NOT_FOUND"));
    }

    private static String at(LocalDateTime t) {
        return t.atZone(SEOUL).toOffsetDateTime().toString();
    }

    private static Map<String, Object> run(
            String recordId, LocalDateTime start, long meters, int seconds) {
        List<LocationPoint> points =
                RunFixtures.straightLine(start, (double) meters / seconds, seconds);
        Map<String, Object> body = new HashMap<>();
        body.put("recordId", recordId);
        body.put("startedAt", at(start));
        body.put("endedAt", at(start.plusSeconds(seconds)));
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

    private JsonNode awaitRunFinished(Session viewer, long crewId) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            for (JsonNode line : history(viewer, crewId)) {
                if ("RUN_FINISHED".equals(line.get("messageKind").asText())) {
                    return line;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("러닝 완료 시스템 메시지가 10초 안에 오지 않았다");
    }

    @Test
    @DisplayName("크루원이 유효 러닝을 올리면 크루 채팅에 RUN_FINISHED(닉네임·거리) 한 건, 다시 올려도 한 건, CREW_CHAT 알림은 없다")
    void runUpload_postsSystemMessage() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        long crewId = crewOf(leader, a);
        String recordId = UUID.randomUUID().toString();
        Map<String, Object> body = run(recordId, LocalDateTime.now().plusMinutes(1), 5_021, 1_800);

        call(a, post("/api/v1/runs"), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        JsonNode line = awaitRunFinished(leader, crewId);
        assertThat(line.get("authorId").asLong()).isEqualTo(a.memberId());
        assertThat(line.get("authorRole").asText()).isEqualTo("MEMBER");
        assertThat(line.get("runDistanceMeters").asLong()).isEqualTo(5_021);
        assertThat(line.get("content").asText()).endsWith("님이 5.02km를 달렸어요");
        assertThat(line.get("mine").asBoolean()).isFalse();

        call(a, post("/api/v1/runs"), body).andExpect(status().isOk());
        Thread.sleep(300);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_chat_message where crew_id = ?"
                                        + " and kind = 'RUN_FINISHED'",
                                Integer.class,
                                crewId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from notification where recipient_id = ?"
                                        + " and type = 'CREW_CHAT'",
                                Integer.class,
                                leader.memberId()))
                .isZero();
    }
}
