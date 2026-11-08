package com.a207.runkk.web.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.crew.JpegSamples;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 신고 엔드포인트(설계 5.7.1 POST /reports, GET /admin/reports, POST /admin/reports/{caseId}/hide·dismiss,
 * 부록 F F.2.2, FR-214~FR-216). 3장 T-28의 요청 단위 케이스(TC-6718~TC-6724, TC-6734)와 숨김 규칙 종류별 7건(H-32,
 * TC-6727~TC-6733)을 인증 필터·OperatorCheckInterceptor를 거쳐 test 프로필(H2 + 내장 Redis)에서 본다.
 * 동시성(TC-6716·6717·6725)은 실 PostgreSQL의 ReportThreadIntegrationTest가 본다.
 */
@DisplayName("web.report·web.admin.report 신고 엔드포인트")
class ReportControllerTest extends WebTestSupport {

    private static final AtomicInteger CREWS = new AtomicInteger();
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String OPERATOR = "report-op";
    private static final String ACTOR = "KAKAO:" + OPERATOR;

    @Autowired OperationSettingService settings;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void whitelist() {
        settings.change(
                "test-setup",
                SettingKey.OPERATOR_WHITELIST,
                "{\"accounts\":{\"KAKAO\":[\"admin-op\",\"" + OPERATOR + "\"]}}");
    }

    /** 크루장 l(글쓴이)과 크루원 r[0..4], 크루 밖 o. */
    private record Crew(long id, Session l, List<Session> r, Session o) {}

    private Crew crew() throws Exception {
        Session l = signUp();
        Map<String, Object> form = new HashMap<>();
        form.put("name", "신고크루" + CREWS.incrementAndGet() + "번");
        form.put("intro", "함께 달려요");
        form.put("tags", List.of("WEEKDAY_EVENING"));
        form.put("visibility", "PUBLIC");
        form.put("joinPolicy", "FREE");
        long id = json(call(l, post("/api/v1/crews"), form)).get("id").asLong();
        List<Session> members = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Session s = signUp();
            call(s, post("/api/v1/crews/join"), Map.of("crewId", id))
                    .andExpect(jsonPath("$.status").value("JOINED"));
            members.add(s);
        }
        return new Crew(id, l, members, signUp());
    }

    private ResultActions call(Session s, MockHttpServletRequestBuilder req, Object body)
            throws Exception {
        MockHttpServletRequestBuilder r = s == null ? req : as(s, req);
        if (body != null) {
            r = r.contentType(MediaType.APPLICATION_JSON).content(write(body));
        }
        return mvc.perform(r);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return body(result.andReturn().getResponse().getContentAsString());
    }

    private long write(Session s, long crewId, Map<String, Object> body) throws Exception {
        return json(call(s, post("/api/v1/crews/" + crewId + "/posts"), body)
                        .andExpect(status().isCreated()))
                .get("id")
                .asLong();
    }

    private static String inHours(long hours) {
        return OffsetDateTime.now(SEOUL).plusHours(hours).withNano(0).toString();
    }

    private ResultActions report(Session s, String kind, long targetId, String reason)
            throws Exception {
        return call(
                s,
                post("/api/v1/reports"),
                Map.of("targetKind", kind, "targetId", targetId, "reason", reason));
    }

    /** 서로 다른 크루원 셋이 신고해 자동 숨김. 셋째 응답이 AUTO_HIDDEN이다. */
    private void autoHide(Crew c, String kind, long targetId) throws Exception {
        for (int i = 0; i < 2; i++) {
            report(c.r().get(i), kind, targetId, "ABUSE")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("RECEIVED"));
        }
        report(c.r().get(2), kind, targetId, "SPAM")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTO_HIDDEN"));
    }

    private String operatorBearer() throws Exception {
        return "Bearer "
                + json(mvc.perform(
                                        post("/api/v1/admin/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        write(
                                                                Map.of(
                                                                        "provider",
                                                                        "KAKAO",
                                                                        "providerToken",
                                                                        OPERATOR))))
                                .andExpect(status().isOk()))
                        .get("adminToken")
                        .asText();
    }

    private ResultActions admin(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header("Authorization", operatorBearer()));
    }

    /** 관리툴 목록에서 그 대상의 사례 한 줄(상태 무관). */
    private JsonNode caseOf(String kind, long targetId) throws Exception {
        for (String status : List.of("RECEIVED", "AUTO_HIDDEN", "HIDDEN", "DISMISSED")) {
            // 기본 크기(30)로 잘리므로 다음 커서를 따라 끝까지 읽는다
            String cursor = null;
            do {
                MockHttpServletRequestBuilder req =
                        get("/api/v1/admin/reports").param("status", status).param("size", "100");
                JsonNode page = json(admin(cursor == null ? req : req.param("cursor", cursor)));
                for (JsonNode item : page.get("cases")) {
                    if (item.get("targetKind").asText().equals(kind)
                            && item.get("targetId").asLong() == targetId) {
                        return item;
                    }
                }
                cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
            } while (cursor != null);
        }
        throw new AssertionError("사례 없음: " + kind + " " + targetId);
    }

    private List<Long> visiblePostIds(Session s, long crewId) throws Exception {
        List<Long> ids = new ArrayList<>();
        json(call(s, get("/api/v1/crews/" + crewId + "/posts"), null))
                .get("posts")
                .forEach(p -> ids.add(p.get("postId").asLong()));
        return ids;
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    @DisplayName("POST /reports: 토큰 없으면 401, 필수 항목 빠짐·기타 내용 없음·201자는 400, 정상 201 RECEIVED")
    void submit_validation() throws Exception {
        Crew c = crew();
        long cheer = write(c.l(), c.id(), Map.of("kind", "CHEER", "body", "화이팅"));
        call(
                        null,
                        post("/api/v1/reports"),
                        Map.of("targetKind", "CHEER", "targetId", cheer, "reason", "SPAM"))
                .andExpect(status().isUnauthorized());
        call(c.r().get(0), post("/api/v1/reports"), Map.of("targetId", cheer, "reason", "SPAM"))
                .andExpect(status().isBadRequest());
        call(
                        c.r().get(0),
                        post("/api/v1/reports"),
                        Map.of("targetKind", "PROFILE", "targetId", cheer, "reason", "SPAM"))
                .andExpect(status().isBadRequest());
        report(c.r().get(0), "CHEER", cheer, "OTHER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        call(
                        c.r().get(0),
                        post("/api/v1/reports"),
                        Map.of(
                                "targetKind",
                                "CHEER",
                                "targetId",
                                cheer,
                                "reason",
                                "OTHER",
                                "detail",
                                "가".repeat(201)))
                .andExpect(status().isBadRequest());
        assertThat(count("select count(*) from report_case where target_id = ?", cheer)).isZero();
        call(
                        c.r().get(0),
                        post("/api/v1/reports"),
                        Map.of(
                                "targetKind",
                                "CHEER",
                                "targetId",
                                cheer,
                                "reason",
                                "OTHER",
                                "detail",
                                "  가".repeat(1) + "나".repeat(199) + "  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").isNumber())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
        assertThat(
                        jdbc.queryForObject(
                                "select detail from content_report r join report_case c"
                                        + " on r.case_id = c.id where c.target_id = ?",
                                String.class,
                                cheer))
                .hasSize(200);
    }

    @Test
    @DisplayName(
            "TC-6734·TC-6718: 종류 불일치·없는 ID는 REPORT_TARGET_NOT_FOUND, 크루 밖 NOT_CREW_MEMBER, 본인 글"
                    + " TARGET_INVALID, 같은 사람 재신고 ALREADY_REPORTED")
    void submit_rejections() throws Exception {
        Crew c = crew();
        long poll =
                write(
                        c.l(),
                        c.id(),
                        Map.of(
                                "kind",
                                "POLL",
                                "body",
                                "모일까요?",
                                "pollMethod",
                                "YES_NO",
                                "closesAt",
                                inHours(24)));
        Session r1 = c.r().get(0);
        report(r1, "CHEER", poll, "SPAM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_TARGET_NOT_FOUND"));
        report(r1, "BOARD", 987_654_321L, "SPAM")
                .andExpect(jsonPath("$.code").value("REPORT_TARGET_NOT_FOUND"));
        report(c.o(), "POLL", poll, "SPAM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CREW_MEMBER"));
        report(c.l(), "POLL", poll, "SPAM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TARGET_INVALID"));

        report(r1, "POLL", poll, "SPAM").andExpect(status().isCreated());
        report(c.r().get(1), "POLL", poll, "ABUSE").andExpect(status().isCreated());
        report(r1, "POLL", poll, "ABUSE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
        assertThat(visiblePostIds(r1, c.id())).contains(poll);
        JsonNode item = caseOf("POLL", poll);
        assertThat(item.get("status").asText()).isEqualTo("RECEIVED");
        assertThat(item.get("reporterCount").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName(
            "TC-6721·TC-6722·TC-6724: 목록(숨김 미리보기·사유별 수·오프셋 시각), 숨김 200·감사·크루 홈 알림, 다시 처리 409, 권한"
                    + " 403")
    void adminList_hide_andGuards() throws Exception {
        Crew c = crew();
        long cheer = write(c.l(), c.id(), Map.of("kind", "CHEER", "body", "보이는 응원"));
        long hiddenCheer = write(c.l(), c.id(), Map.of("kind", "CHEER", "body", "숨길 응원"));
        report(c.r().get(0), "CHEER", cheer, "ABUSE").andExpect(status().isCreated());
        report(c.r().get(1), "CHEER", cheer, "SPAM").andExpect(status().isCreated());
        autoHide(c, "CHEER", hiddenCheer);

        JsonNode open = caseOf("CHEER", cheer);
        assertThat(open.get("contentPreview").asText()).isEqualTo("보이는 응원");
        assertThat(open.get("targetMissing").asBoolean()).isFalse();
        assertThat(open.get("crewId").asLong()).isEqualTo(c.id());
        assertThat(open.get("authorId").asLong()).isEqualTo(c.l().memberId());
        assertThat(open.get("reasons").get("ABUSE").asInt()).isEqualTo(1);
        assertThat(open.get("reasons").get("SPAM").asInt()).isEqualTo(1);
        assertThat(open.get("firstReportedAt").asText()).endsWith("+09:00");
        JsonNode auto = caseOf("CHEER", hiddenCheer);
        assertThat(auto.get("status").asText()).isEqualTo("AUTO_HIDDEN");
        assertThat(auto.get("contentPreview").asText()).isEqualTo("숨길 응원");

        long caseId = open.get("caseId").asLong();
        admin(post("/api/v1/admin/reports/" + caseId + "/hide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.status").value("HIDDEN"));
        assertThat(visiblePostIds(c.r().get(3), c.id())).doesNotContain(cheer, hiddenCheer);
        assertThat(
                        count(
                                "select count(*) from content_report where case_id = ?"
                                        + " and resolved = true",
                                caseId))
                .isEqualTo(2);
        assertThat(
                        count(
                                "select count(*) from audit_record where action = 'REPORT_HIDE'"
                                        + " and target_id = ? and actor_id = ?"
                                        + " and actor_role = 'OPERATOR'",
                                String.valueOf(caseId),
                                ACTOR))
                .isEqualTo(1);
        for (int i = 0; i < 2; i++) {
            assertThat(
                            count(
                                    "select count(*) from notification where recipient_id = ?"
                                            + " and type = 'REPORT_RESOLVED'"
                                            + " and target_screen = 'CREW_HOME' and target_id = ?",
                                    c.r().get(i).memberId(),
                                    String.valueOf(c.id())))
                    .isEqualTo(1);
        }

        admin(post("/api/v1/admin/reports/" + caseId + "/hide"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_DECIDED"));
        admin(post("/api/v1/admin/reports/" + caseId + "/dismiss"))
                .andExpect(jsonPath("$.code").value("REPORT_DECIDED"));
        admin(post("/api/v1/admin/reports/987654321/hide"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        mvc.perform(post("/api/v1/admin/reports/" + caseId + "/hide"))
                .andExpect(status().isForbidden());
        call(c.r().get(0), post("/api/v1/admin/reports/" + caseId + "/dismiss"), null)
                .andExpect(status().isForbidden());
        call(c.r().get(0), get("/api/v1/admin/reports"), null).andExpect(status().isForbidden());
        admin(get("/api/v1/admin/reports").param("size", "0")).andExpect(status().isBadRequest());
        admin(get("/api/v1/admin/reports").param("size", "101")).andExpect(status().isBadRequest());
        assertThat(
                        count(
                                "select count(*) from audit_record where target_id = ?"
                                        + " and action in ('REPORT_HIDE', 'REPORT_DISMISS')",
                                String.valueOf(caseId)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "TC-6723·TC-6719·TC-6720: 게시글(사진 2장) 기각은 다시 보이게 하고, 옛 신고자 재신고는 ALREADY_REPORTED, 새 신고자"
                    + " 셋은 다시 자동 숨김")
    void dismiss_thenReopen() throws Exception {
        Crew c = crew();
        byte[] jpeg = JpegSamples.clean(new byte[16]);
        long board =
                json(mvc.perform(
                                        as(
                                                c.l(),
                                                multipart(
                                                                "/api/v1/crews/"
                                                                        + c.id()
                                                                        + "/board-posts")
                                                        .file(photo(jpeg))
                                                        .file(photo(jpeg))
                                                        .param("body", "사진 두 장")))
                                .andExpect(status().isCreated()))
                        .get("id")
                        .asLong();
        autoHide(c, "BOARD", board);
        assertThat(
                        json(call(c.r().get(3), get("/api/v1/crews/" + c.id() + "/album"), null))
                                .get("photos"))
                .isEmpty();

        long caseId = caseOf("BOARD", board).get("caseId").asLong();
        admin(post("/api/v1/admin/reports/" + caseId + "/dismiss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
        assertThat(visiblePostIds(c.r().get(3), c.id())).contains(board);
        assertThat(
                        json(call(c.r().get(3), get("/api/v1/crews/" + c.id() + "/album"), null))
                                .get("photos"))
                .hasSize(2);
        assertThat(
                        count(
                                "select count(*) from notification where type = 'REPORT_RESOLVED'"
                                        + " and target_id = ?",
                                String.valueOf(c.id())))
                .isEqualTo(3);
        assertThat(
                        count(
                                "select count(*) from audit_record where action = 'REPORT_DISMISS'"
                                        + " and target_id = ?",
                                String.valueOf(caseId)))
                .isEqualTo(1);

        report(c.r().get(0), "BOARD", board, "SPAM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
        assertThat(caseOf("BOARD", board).get("status").asText()).isEqualTo("DISMISSED");

        Session r4 = c.r().get(3);
        Session r5 = c.r().get(4);
        Session r6 = signUp();
        call(r6, post("/api/v1/crews/join"), Map.of("crewId", c.id()))
                .andExpect(jsonPath("$.status").value("JOINED"));
        report(r4, "BOARD", board, "SPAM").andExpect(jsonPath("$.status").value("RECEIVED"));
        assertThat(caseOf("BOARD", board).get("status").asText()).isEqualTo("RECEIVED");
        report(r5, "BOARD", board, "SPAM").andExpect(jsonPath("$.status").value("RECEIVED"));
        assertThat(visiblePostIds(r4, c.id())).contains(board);
        report(r6, "BOARD", board, "SPAM").andExpect(jsonPath("$.status").value("AUTO_HIDDEN"));
        assertThat(visiblePostIds(r4, c.id())).doesNotContain(board);
        assertThat(caseOf("BOARD", board).get("reporterCount").asInt()).isEqualTo(6);
    }

    private static MockMultipartFile photo(byte[] bytes) {
        return new MockMultipartFile("photos", "p.jpg", "image/jpeg", bytes);
    }

    @Test
    @DisplayName("TC-6727 H-32 응원글: 숨긴 응원글은 목록에 없고 작성자 삭제·새 신고는 409, 관리툴 미리보기는 보인다")
    void hiddenCheer() throws Exception {
        Crew c = crew();
        long cheer = write(c.l(), c.id(), Map.of("kind", "CHEER", "body", "응원"));
        autoHide(c, "CHEER", cheer);
        assertThat(visiblePostIds(c.r().get(3), c.id())).doesNotContain(cheer);
        call(c.l(), delete("/api/v1/posts/" + cheer), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        report(c.r().get(3), "CHEER", cheer, "SPAM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_TARGET_NOT_FOUND"));
        assertThat(caseOf("CHEER", cheer).get("contentPreview").asText()).isEqualTo("응원");
    }

    private void hiddenSchedule(Map<String, Object> form) throws Exception {
        Crew c = crew();
        long schedule = write(c.l(), c.id(), form);
        autoHide(c, "SCHEDULE", schedule);
        assertThat(visiblePostIds(c.r().get(3), c.id())).doesNotContain(schedule);
        call(
                        c.r().get(3),
                        put("/api/v1/posts/" + schedule + "/attendance"),
                        Map.of("answer", "GOING"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        call(c.l(), delete("/api/v1/posts/" + schedule), null)
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        report(c.r().get(3), "SCHEDULE", schedule, "SPAM")
                .andExpect(jsonPath("$.code").value("REPORT_TARGET_NOT_FOUND"));
    }

    @Test
    @DisplayName(
            "TC-6728 H-32 크루런 일정: 목록에 없음, 참석 응답·삭제·새 신고"
                    + " 409(POST_NOT_FOUND·REPORT_TARGET_NOT_FOUND)")
    void hiddenCrewRun() throws Exception {
        Map<String, Object> run = new HashMap<>();
        run.put("kind", "CREW_RUN");
        run.put("body", "같이 뛰어요");
        run.put("distanceMeters", 3000);
        run.put("scheduledAt", inHours(26));
        hiddenSchedule(run);
    }

    @Test
    @DisplayName("TC-6729 H-32 대면 모임: 크루런 일정과 같은 결과")
    void hiddenMeetup() throws Exception {
        hiddenSchedule(
                Map.of(
                        "kind",
                        "MEETUP",
                        "scheduledAt",
                        inHours(26),
                        "placeName",
                        "한강공원",
                        "capacity",
                        5));
    }

    @Test
    @DisplayName("TC-6730 H-32 고정 공지: 고정 목록에서 빠지고 고정 해제·삭제·새 신고 409, 고정 한도는 보이는 공지만 센다")
    void hiddenPinnedNotice() throws Exception {
        Crew c = crew();
        List<Long> notices = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long notice = write(c.l(), c.id(), Map.of("kind", "NOTICE", "body", "공지" + i));
            call(c.l(), put("/api/v1/posts/" + notice + "/pin"), Map.of("pinned", true))
                    .andExpect(status().isOk());
            notices.add(notice);
        }
        long hidden = notices.get(0);
        autoHide(c, "NOTICE", hidden);
        assertThat(
                        json(call(
                                        c.r().get(3),
                                        get("/api/v1/crews/" + c.id() + "/posts")
                                                .param("pinnedOnly", "true"),
                                        null))
                                .get("posts"))
                .hasSize(2);
        call(c.l(), put("/api/v1/posts/" + hidden + "/pin"), Map.of("pinned", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        call(c.l(), delete("/api/v1/posts/" + hidden), null)
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        report(c.r().get(3), "NOTICE", hidden, "SPAM")
                .andExpect(jsonPath("$.code").value("REPORT_TARGET_NOT_FOUND"));
        // 보이는 고정 공지가 2개라 새 고정 하나는 받는다. 그 뒤 기각하면 숨겼던 공지는 고정이 풀려 다시 보인다(D-154)
        long fourth = write(c.l(), c.id(), Map.of("kind", "NOTICE", "body", "새 공지"));
        call(c.l(), put("/api/v1/posts/" + fourth + "/pin"), Map.of("pinned", true))
                .andExpect(status().isOk());
        long caseId = caseOf("NOTICE", hidden).get("caseId").asLong();
        admin(post("/api/v1/admin/reports/" + caseId + "/dismiss")).andExpect(status().isOk());
        JsonNode pinned =
                json(call(
                                c.r().get(3),
                                get("/api/v1/crews/" + c.id() + "/posts")
                                        .param("pinnedOnly", "true"),
                                null))
                        .get("posts");
        assertThat(pinned).hasSize(3);
        assertThat(visiblePostIds(c.r().get(3), c.id())).contains(hidden);
    }

    @Test
    @DisplayName("TC-6731 H-32 투표: 목록에 없고 응답·새 신고 409")
    void hiddenPoll() throws Exception {
        Crew c = crew();
        long poll =
                write(
                        c.l(),
                        c.id(),
                        Map.of(
                                "kind",
                                "POLL",
                                "body",
                                "모일까요?",
                                "pollMethod",
                                "YES_NO",
                                "closesAt",
                                inHours(1)));
        autoHide(c, "POLL", poll);
        assertThat(visiblePostIds(c.r().get(3), c.id())).doesNotContain(poll);
        call(c.r().get(3), put("/api/v1/posts/" + poll + "/poll-response"), Map.of("optionNo", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        report(c.r().get(3), "POLL", poll, "SPAM")
                .andExpect(jsonPath("$.code").value("REPORT_TARGET_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-6732 H-32 게시글: 게시판·앨범에 없고 사진 조회·삭제·새 신고 409")
    void hiddenBoard() throws Exception {
        Crew c = crew();
        long board =
                json(mvc.perform(
                                        as(
                                                c.l(),
                                                multipart(
                                                                "/api/v1/crews/"
                                                                        + c.id()
                                                                        + "/board-posts")
                                                        .file(photo(JpegSamples.clean(new byte[8])))
                                                        .param("body", "사진")))
                                .andExpect(status().isCreated()))
                        .get("id")
                        .asLong();
        String url =
                json(call(c.r().get(3), get("/api/v1/crews/" + c.id() + "/album"), null))
                        .get("photos")
                        .get(0)
                        .get("url")
                        .asText();
        autoHide(c, "BOARD", board);
        assertThat(visiblePostIds(c.r().get(3), c.id())).doesNotContain(board);
        assertThat(
                        json(call(c.r().get(3), get("/api/v1/crews/" + c.id() + "/album"), null))
                                .get("photos"))
                .isEmpty();
        call(c.r().get(3), get(url), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        call(c.l(), delete("/api/v1/posts/" + board), null)
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        report(c.r().get(3), "BOARD", board, "SPAM")
                .andExpect(jsonPath("$.code").value("REPORT_TARGET_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-6733·TC-6726 H-32 채팅: 숨긴 메시지는 기록에 없고 삭제·리액션·새 신고 409, 기각 뒤 다시 나온다")
    void hiddenChatMessage() throws Exception {
        Crew c = crew();
        String messages = "/api/v1/crews/" + c.id() + "/chat/messages";
        long message =
                json(call(
                                c.l(),
                                post(messages),
                                Map.of(
                                        "clientMessageId",
                                        UUID.randomUUID().toString(),
                                        "content",
                                        "신고될 메시지")))
                        .get("messageId")
                        .asLong();
        autoHide(c, "CHAT_MESSAGE", message);
        assertThat(messageIds(c.r().get(3), messages)).doesNotContain(message);
        call(c.l(), delete("/api/v1/chat-messages/" + message), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_MESSAGE_NOT_FOUND"));
        call(c.r().get(3), put("/api/v1/chat-messages/" + message + "/reactions/LIKE"), null)
                .andExpect(jsonPath("$.code").value("CHAT_MESSAGE_NOT_FOUND"));
        report(c.r().get(3), "CHAT_MESSAGE", message, "SPAM")
                .andExpect(jsonPath("$.code").value("REPORT_TARGET_NOT_FOUND"));
        JsonNode item = caseOf("CHAT_MESSAGE", message);
        assertThat(item.get("contentPreview").asText()).isEqualTo("신고될 메시지");

        admin(post("/api/v1/admin/reports/" + item.get("caseId").asLong() + "/dismiss"))
                .andExpect(status().isOk());
        assertThat(messageIds(c.r().get(3), messages)).contains(message);
    }

    private List<Long> messageIds(Session s, String path) throws Exception {
        List<Long> ids = new ArrayList<>();
        json(call(s, get(path), null))
                .get("messages")
                .forEach(m -> ids.add(m.get("messageId").asLong()));
        return ids;
    }

    /**
     * 크루 밖 회원의 두 오류 경로를 고정한다(구현 결정, 설계 통일은 설계서 갱신 요청). 크루 글·댓글 쓰기(S3a)는 PermissionDeniedException →
     * 403 PERMISSION_DENIED, 채팅(S3b)·신고(S3c)는 BusinessRuleException(NOT_CREW_MEMBER) → 409
     * NOT_CREW_MEMBER. 앱은 둘 다 처리한다. 설계를 하나로 정하면 이 시험을 함께 바꾼다.
     */
    @Test
    @DisplayName("크루 밖 회원: 글 쓰기는 403 PERMISSION_DENIED, 채팅 보내기·신고는 409 NOT_CREW_MEMBER(두 경로 고정)")
    void outsider_twoErrorPaths() throws Exception {
        Crew c = crew();
        long cheer = write(c.l(), c.id(), Map.of("kind", "CHEER", "body", "응원해요"));

        call(
                        c.o(),
                        post("/api/v1/crews/" + c.id() + "/posts"),
                        Map.of("kind", "CHEER", "body", "밖"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        call(
                        c.o(),
                        post("/api/v1/crews/" + c.id() + "/chat/messages"),
                        Map.of("clientMessageId", UUID.randomUUID().toString(), "content", "밖"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CREW_MEMBER"));
        report(c.o(), "CHEER", cheer, "SPAM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CREW_MEMBER"));
    }

    @Test
    @DisplayName("관리툴 목록: 지워진 대상은 targetMissing·미리보기 없음, 커서·크기로 나눠 읽는다")
    void adminList_missingTargetAndPaging() throws Exception {
        Crew c = crew();
        long gone = write(c.l(), c.id(), Map.of("kind", "CHEER", "body", "곧 지움"));
        report(c.r().get(0), "CHEER", gone, "SPAM").andExpect(status().isCreated());
        long kept = write(c.l(), c.id(), Map.of("kind", "CHEER", "body", "남김"));
        report(c.r().get(0), "CHEER", kept, "SPAM").andExpect(status().isCreated());
        call(c.l(), delete("/api/v1/posts/" + gone), null).andExpect(status().isNoContent());
        JsonNode missing = caseOf("CHEER", gone);
        assertThat(missing.get("targetMissing").asBoolean()).isTrue();
        assertThat(missing.get("contentPreview").isNull()).isTrue();

        JsonNode first = json(admin(get("/api/v1/admin/reports").param("size", "1")));
        assertThat(first.get("cases")).hasSize(1);
        long next = first.get("nextCursor").asLong();
        assertThat(next).isEqualTo(first.get("cases").get(0).get("caseId").asLong());
        JsonNode second =
                json(
                        admin(
                                get("/api/v1/admin/reports")
                                        .param("size", "1")
                                        .param("cursor", String.valueOf(next))));
        assertThat(second.get("cases").get(0).get("caseId").asLong()).isNotEqualTo(next);
        admin(get("/api/v1/admin/reports").param("size", "1").param("cursor", "987654321"))
                .andExpect(status().isBadRequest());
    }
}
