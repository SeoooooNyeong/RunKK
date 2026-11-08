package com.a207.runkk.web.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.battle.BattleJudged;
import com.a207.runkk.battle.BattleType;
import com.a207.runkk.battle.Judgement;
import com.a207.runkk.battle.Side;
import com.a207.runkk.battle.Verdict;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.reward.AccrualReason;
import com.a207.runkk.reward.RewardService;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이벤트 앱 API와 관리 API(S15P31A207-275): 관리 API는 운영자 토큰만(없음, 회원 JWT는 403), 공개 전환이 앱 목록에 바로 보이고, 셀럽 대결
 * 승리가 응모와 코치 승리 포인트(커밋 뒤 수신자)로 이어진다.
 */
@DisplayName("web.event 이벤트 API")
class EventControllerTest extends WebTestSupport {

    private static final String OPERATOR = "event-op";

    @Autowired OperationSettingService settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired RewardService rewards;

    @BeforeEach
    void whitelist() {
        settings.change(
                "test-setup",
                SettingKey.OPERATOR_WHITELIST,
                "{\"accounts\":{\"KAKAO\":[\"" + OPERATOR + "\"]}}");
    }

    private String operatorBearer() throws Exception {
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
                                                                        OPERATOR))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        return "Bearer " + session.get("adminToken").asText();
    }

    private static Map<String, Object> event(String slug, Object config, boolean published) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        Map<String, Object> body = new HashMap<>();
        body.put("slug", slug);
        body.put("title", "코치님을 이겨라");
        body.put("subtitle", "코치님 기록을 넘고 GS 상품권 받기");
        body.put("kind", "COACH_CELEB");
        body.put("config", config);
        body.put("startsAt", now.minusDays(1).toString());
        body.put("endsAt", now.plusDays(1).toString());
        body.put("bannerImageUrl", "/events/" + slug + "/banner.png");
        body.put("thumbnailImageUrl", "/events/" + slug + "/thumb.png");
        body.put("detailUrl", "/events/" + slug + "/");
        body.put("sortOrder", 1);
        body.put("published", published);
        return body;
    }

    private long create(String bearer, Map<String, Object> event) throws Exception {
        return body(mvc.perform(
                                post("/api/v1/admin/events")
                                        .header("Authorization", bearer)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(write(event)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asLong();
    }

    private JsonNode myEvents(Session s) throws Exception {
        return body(mvc.perform(as(s, get("/api/v1/events")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("events");
    }

    private static JsonNode find(JsonNode events, String slug) {
        for (JsonNode e : events) {
            if (slug.equals(e.get("slug").asText())) {
                return e;
            }
        }
        return null;
    }

    @Test
    @DisplayName("관리 API는 관리툴 토큰이 없거나 회원 JWT면 403, 앱 API는 로그인 없이 401")
    void adminApi_requiresOperator() throws Exception {
        Session member = signUp();
        List<MockHttpServletRequestBuilder> requests =
                List.of(
                        get("/api/v1/admin/events"),
                        post("/api/v1/admin/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(event("x-" + UUID.randomUUID(), "{}", true))),
                        put("/api/v1/admin/events/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"),
                        put("/api/v1/admin/events/1/published")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"published\":true}"));
        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
            mvc.perform(request.header("Authorization", member.bearer()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/events")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("운영자가 만든 공개 이벤트가 앱 목록에 보이고, 비공개로 바꾸면 목록에서 빠지고 내 참여는 404")
    void publishToggle_isVisibleInApp() throws Exception {
        String bearer = operatorBearer();
        String slug = "coach-" + UUID.randomUUID().toString().substring(0, 8);
        long id =
                create(
                        bearer,
                        event(slug, Map.of("celebrityIds", List.of(11), "pointPerWin", 500), true));
        Session member = signUp();

        JsonNode row = find(myEvents(member), slug);
        assertThat(row).isNotNull();
        assertThat(row.get("status").asText()).isEqualTo("ONGOING");
        assertThat(row.get("entryCount").asInt()).isZero();
        assertThat(row.get("maxEntryCount").asInt()).isEqualTo(1);
        assertThat(row.get("detailUrl").asText()).isEqualTo("/events/" + slug + "/");

        mvc.perform(
                        put("/api/v1/admin/events/" + id + "/published")
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"published\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));

        assertThat(find(myEvents(member), slug)).isNull();
        mvc.perform(as(member, get("/api/v1/events/" + slug + "/me")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        mvc.perform(
                        post("/api/v1/admin/events")
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(event(slug, "{}", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VALUE_INVALID"));
    }

    @Test
    @DisplayName("셀럽 대결 승리가 응모 1회가 되고, 판정 커밋 뒤 코치 승리 포인트 500P가 한 번만 쌓인다")
    void celebrityWin_entersAndGrantsPointOnce() throws Exception {
        String bearer = operatorBearer();
        String slug = "coach-" + UUID.randomUUID().toString().substring(0, 8);
        long celebrityA = 9_000 + (long) (Math.random() * 900);
        long celebrityB = celebrityA + 1;
        long eventId =
                create(
                        bearer,
                        event(
                                slug,
                                Map.of(
                                        "celebrityIds",
                                        List.of(celebrityA, celebrityB),
                                        "pointPerWin",
                                        500),
                                true));
        Session member = signUp();
        LocalDateTime judgedAt =
                LocalDateTime.now().minusMinutes(5).truncatedTo(ChronoUnit.SECONDS);
        long battleId = finishedCelebrityWin(member.memberId(), celebrityA, judgedAt);

        mvc.perform(as(member, get("/api/v1/events/" + slug + "/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entryCount").value(1))
                .andExpect(jsonPath("$.maxEntryCount").value(2))
                .andExpect(jsonPath("$.items[0].key").value("celebrity-" + celebrityA))
                .andExpect(jsonPath("$.items[0].done").value(true))
                .andExpect(jsonPath("$.items[0].doneAt").value(judgedAt.toString()))
                .andExpect(jsonPath("$.items[1].done").value(false));

        for (int i = 0; i < 2; i++) {
            transactions.executeWithoutResult(
                    status ->
                            publisher.publishEvent(
                                    new BattleJudged(
                                            battleId,
                                            BattleType.CELEBRITY,
                                            new Judgement(
                                                    Map.of(
                                                            Side.member(member.memberId()),
                                                            Verdict.WIN,
                                                            Side.OPPONENT,
                                                            Verdict.LOSE),
                                                    Side.member(member.memberId()),
                                                    false,
                                                    judgedAt),
                                            judgedAt)));
        }

        assertThat(
                        rewards.accruedFor(
                                AccrualReason.EVENT,
                                eventId + ":celebrity-" + celebrityA,
                                member.memberId()))
                .isEqualTo(500);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from event_point_grant where member_id = ?"
                                        + " and event_id = ?",
                                Integer.class,
                                member.memberId(),
                                eventId))
                .isEqualTo(1);
    }

    /** 회원이 이긴 끝난 셀럽 대결 한 건(판정 결과 칼럼만 채운다). */
    private long finishedCelebrityWin(long memberId, long celebrityId, LocalDateTime judgedAt) {
        jdbc.update(
                "insert into battle (type, distance_meters, status, created_at, opponent_kind,"
                        + " opponent_record_id, opponent_name, opponent_tier,"
                        + " opponent_arrival_millis, judged_at, corrected, winner_member_id,"
                        + " winner_opponent, version) values ('CELEBRITY', 5000, 'FINISHED', ?,"
                        + " 'CELEBRITY', ?, '코치', 'UNRANKED', 1800000, ?, false, ?, false, 0)",
                Timestamp.valueOf(judgedAt.minusMinutes(30)),
                String.valueOf(celebrityId),
                Timestamp.valueOf(judgedAt),
                memberId);
        return jdbc.queryForObject(
                "select max(id) from battle where winner_member_id = ?", Long.class, memberId);
    }
}
