package com.a207.runkk.web.admin.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.SocialAccount;
import com.a207.runkk.operator.OperatorId;
import com.a207.runkk.operator.OperatorTokenIssuer;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 운영자 전용 랭킹 다시 계산(POST /api/v1/admin/rankings/recompute → RankingService.recompute, 감사
 * RANKING_RECOMPUTED). 운영자가 아니면 403, 운영자는 200과 건수, 바로 /rankings/personal에 반영, 실행마다 감사 1건, 동시 호출은
 * 하나만 돌고 나머지는 409이며 스냅숏은 종류·기간별 하나(4개)로 남는다.
 */
@DisplayName("web.admin 랭킹 다시 계산")
class RankingAdminControllerTest extends WebTestSupport {

    private static final String PATH = "/api/v1/admin/rankings/recompute";
    private static final String OPERATOR = "ranking-op";
    private static final String ACTOR = "KAKAO:" + OPERATOR;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired OperationSettingService settings;
    @Autowired OperatorTokenIssuer operatorTokens;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void whitelist() {
        settings.change(
                "test-setup",
                SettingKey.OPERATOR_WHITELIST,
                "{\"accounts\":{\"KAKAO\":[\"admin-op\",\"" + OPERATOR + "\"]}}");
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

    /** 이 운영자가 남긴 RANKING_RECOMPUTED 감사 기록. */
    private List<JsonNode> recomputeAudits(String bearer) throws Exception {
        LocalDate today = LocalDate.now(SEOUL);
        JsonNode audits =
                body(
                        mvc.perform(
                                        get("/api/v1/admin/audits")
                                                .header("Authorization", bearer)
                                                .param("action", "RANKING_RECOMPUTED")
                                                .param("from", today.minusDays(1).toString())
                                                .param("to", today.plusDays(1).toString()))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        List<JsonNode> mine = new ArrayList<>();
        audits.forEach(
                a -> {
                    if (ACTOR.equals(a.get("actorId").asText())) {
                        mine.add(a);
                    }
                });
        return mine;
    }

    private int snapshotCount() {
        return jdbc.queryForObject("select count(*) from ranking_snapshot", Integer.class);
    }

    @Test
    @DisplayName("관리툴 토큰 없음·위조·화이트리스트 밖·회원 JWT면 403 PERMISSION_DENIED")
    void recompute_isForbiddenWithoutOperator() throws Exception {
        String stranger =
                "Bearer "
                        + operatorTokens.issue(
                                new OperatorId(new SocialAccount(ProviderType.KAKAO, "stranger")));
        String member = signUp().bearer();
        mvc.perform(post(PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        for (String header : List.of("Bearer forged.token.value", stranger, member, "Basic x")) {
            mvc.perform(post(PATH).header("Authorization", header))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        }
    }

    @Test
    @DisplayName("운영자는 200과 집계 시각·개인·크루 건수, 바로 /rankings/personal에 반영되고 감사 1건이 남는다")
    void operatorRecompute_isReflectedAndAudited() throws Exception {
        Session me = signUp();
        upload(me, 7_000, 2_100);
        String bearer = operatorBearer();
        int auditsBefore = recomputeAudits(bearer).size();
        JsonNode stale = getJson(me, "/api/v1/rankings/personal");
        assertThat(stale.get("mine").isNull()).as("다시 계산하기 전에는 새 회원 줄이 없다").isTrue();

        JsonNode result =
                body(
                        mvc.perform(post(PATH).header("Authorization", bearer))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(result.get("personalCount").asInt()).isPositive();
        assertThat(result.get("crewCount").asInt()).isNotNegative();
        assertThat(result.get("failedSnapshots").asInt()).isZero();
        LocalDateTime computedAt = LocalDateTime.parse(result.get("computedAt").asText());
        assertThat(computedAt).isAfter(LocalDateTime.now(SEOUL).minusMinutes(1));

        JsonNode fresh = getJson(me, "/api/v1/rankings/personal");
        assertThat(fresh.get("mine").get("subjectId").asLong()).isEqualTo(me.memberId());
        assertThat(fresh.get("mine").get("distanceMeters").asLong()).isEqualTo(7_000);
        assertThat(
                        OffsetDateTime.parse(fresh.get("aggregatedAt").asText())
                                .toLocalDateTime()
                                .truncatedTo(ChronoUnit.SECONDS))
                .isEqualTo(computedAt.truncatedTo(ChronoUnit.SECONDS));

        List<JsonNode> audits = recomputeAudits(bearer);
        assertThat(audits).hasSize(auditsBefore + 1);
        JsonNode audit = audits.get(0);
        assertThat(audit.get("actorRole").asText()).isEqualTo("OPERATOR");
        assertThat(audit.get("targetId").asText()).isEqualTo("RANKING");
        assertThat(audit.get("after").asText())
                .contains("personal=" + result.get("personalCount").asInt())
                .contains("crew=" + result.get("crewCount").asInt())
                .contains("failed=0");
    }

    @Test
    @DisplayName("동시 호출은 하나씩만 돌고(나머지 409 INVALID_STATE) 스냅숏은 4개, 감사는 성공한 수만큼, 끝난 뒤 다시 부르면 200")
    void concurrentRecomputes_keepSnapshotsWhole() throws Exception {
        String bearer = operatorBearer();
        int auditsBefore = recomputeAudits(bearer).size();
        int callers = 4;
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                Callable<MockHttpServletResponse> call =
                        () -> {
                            go.await();
                            return mvc.perform(post(PATH).header("Authorization", bearer))
                                    .andReturn()
                                    .getResponse();
                        };
                futures.add(pool.submit(call));
            }
            go.countDown();
            int ok = 0;
            for (Future<MockHttpServletResponse> f : futures) {
                MockHttpServletResponse response = f.get(30, TimeUnit.SECONDS);
                assertThat(response.getStatus()).isIn(200, 409);
                if (response.getStatus() == 200) {
                    ok++;
                } else {
                    assertThat(body(response.getContentAsString()).get("code").asText())
                            .isEqualTo("INVALID_STATE");
                }
            }
            assertThat(ok).isPositive();
            assertThat(snapshotCount()).as("종류·기간별 스냅숏 하나씩").isEqualTo(4);
            assertThat(recomputeAudits(bearer)).hasSize(auditsBefore + ok);
        } finally {
            pool.shutdownNow();
        }
        mvc.perform(post(PATH).header("Authorization", bearer)).andExpect(status().isOk());
        assertThat(snapshotCount()).isEqualTo(4);
    }

    private JsonNode getJson(Session s, String url) throws Exception {
        return body(
                mvc.perform(as(s, get(url)).param("period", "WEEKLY"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    /** 오늘 6시에 시작한 자유 러닝 하나(web.record 시험과 같은 형식). */
    private void upload(Session s, long meters, int seconds) throws Exception {
        LocalDateTime start = LocalDate.now(SEOUL).atTime(6, 0);
        List<LocationPoint> points =
                RunFixtures.straightLine(start, (double) meters / seconds, seconds);
        Map<String, Object> body = new HashMap<>();
        body.put("recordId", UUID.randomUUID().toString());
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
        mvc.perform(
                        as(s, post("/api/v1/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(body)))
                .andExpect(status().isOk());
    }

    private static String at(LocalDateTime t) {
        return t.atZone(SEOUL).toOffsetDateTime().toString();
    }
}
