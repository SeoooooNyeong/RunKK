package com.a207.runkk.web.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.common.Week;
import com.a207.runkk.member.ShownGender;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * 피그마 맞춤 W7(S15P31A207-298, contract-W7.md): 크루원 목록의 누적 거리·가입일·레벨·나이대·성별(공개 설정 반영)과 정렬, 가입 신청자 보조
 * 정보, 지난 공동 목표 목록(GET /crews/{id}/goals/history). test 프로필(H2 + 내장 Redis).
 */
@DisplayName("web.crew W7 크루원·가입 신청·지난 목표")
class CrewMembersW7ControllerTest extends WebTestSupport {

    private static final AtomicInteger CREWS = new AtomicInteger();

    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

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

    private long createCrew(Session leader, String policy) throws Exception {
        Map<String, Object> form = new HashMap<>();
        form.put("name", "W7크루" + CREWS.incrementAndGet());
        form.put("tags", List.of("WEEKDAY_EVENING"));
        form.put("visibility", "PUBLIC");
        form.put("joinPolicy", policy);
        return json(call(leader, post("/api/v1/crews"), form).andExpect(status().isCreated()))
                .get("id")
                .asLong();
    }

    private void profile(Session s, int birthYear, String gender, String ageVis, String genderVis)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("birthYear", birthYear);
        body.put("gender", gender);
        body.put("ageBandVisibility", ageVis);
        body.put("genderVisibility", genderVis);
        call(s, patch("/api/v1/me/profile"), body).andExpect(status().isOk());
    }

    private void contribute(long crewId, LocalDate weekStart, Long memberId, long meters) {
        jdbc.update(
                "insert into race_contribution (crew_id, week_start, member_id, record_id, meters)"
                        + " values (?, ?, ?, ?, ?)",
                crewId,
                weekStart,
                memberId,
                UUID.randomUUID().toString(),
                meters);
    }

    private void week(long crewId, LocalDate weekStart, Long goal, long total, boolean achieved) {
        jdbc.update(
                "insert into weekly_race (crew_id, week_start, total_meters, goal_meters,"
                        + " achieved_at) values (?, ?, ?, ?, ?)",
                crewId,
                weekStart,
                total,
                goal,
                achieved ? weekStart.atTime(20, 0) : null);
    }

    private LocalDate thisMonday() {
        return Week.of(LocalDateTime.now(clock)).monday();
    }

    private static String ageBand(int birthYear, int year) {
        return ((year - birthYear) / 10 * 10) + "대";
    }

    private static JsonNode find(JsonNode array, long memberId) {
        for (JsonNode n : array) {
            if (n.get("memberId").asLong() == memberId) {
                return n;
            }
        }
        throw new AssertionError("없는 회원 " + memberId);
    }

    @Test
    @DisplayName("크루원 목록: 이 크루 누적 기여 거리 내림차순, 가입일·레벨, 나이대·성별은 공개일 때만, 본인 줄에만 공개 설정")
    void members_carryAccumulatedDistanceAndDisclosedProfile() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        Session b = signUp();
        long crewId = createCrew(leader, "FREE");
        call(a, post("/api/v1/crews/join"), Map.of("crewId", crewId))
                .andExpect(jsonPath("$.status").value("JOINED"));
        call(b, post("/api/v1/crews/join"), Map.of("crewId", crewId))
                .andExpect(jsonPath("$.status").value("JOINED"));
        int year = LocalDate.now(clock).getYear();
        profile(leader, 1990, "FEMALE", "PUBLIC", "PUBLIC");
        profile(a, 1995, "MALE", "PUBLIC", "PRIVATE");
        profile(b, 2001, "FEMALE", "PRIVATE", "PRIVATE");

        LocalDate monday = thisMonday();
        contribute(crewId, monday, a.memberId(), 3_000);
        contribute(crewId, monday.minusWeeks(1), a.memberId(), 4_000);
        contribute(crewId, monday, b.memberId(), 12_000);
        contribute(crewId + 100_000, monday, a.memberId(), 50_000); // 다른 크루 기여는 넣지 않는다
        contribute(crewId, monday, null, 9_999); // 익명 기여는 어느 크루원 것도 아니다

        JsonNode members =
                json(call(a, get("/api/v1/crews/" + crewId + "/members"), null)).get("members");
        List<Long> order = new ArrayList<>();
        members.forEach(m -> order.add(m.get("memberId").asLong()));
        assertThat(order)
                .as("누적 거리 내림차순, 같으면 가입 순")
                .containsExactly(b.memberId(), a.memberId(), leader.memberId());

        JsonNode me = find(members, a.memberId());
        assertThat(me.get("me").asBoolean()).isTrue();
        assertThat(me.get("accumulatedMeters").asLong()).isEqualTo(7_000);
        assertThat(me.get("joinedAt").asText()).endsWith("+09:00");
        assertThat(me.get("level").isInt()).isTrue();
        assertThat(me.get("ageBand").asText()).isEqualTo(ageBand(1995, year));
        assertThat(me.get("gender").asText()).as("본인은 비공개 항목도 본다").isEqualTo("MALE");
        assertThat(me.get("ageBandVisibility").asText()).isEqualTo("PUBLIC");
        assertThat(me.get("genderVisibility").asText()).isEqualTo("PRIVATE");

        JsonNode hidden = find(members, b.memberId());
        assertThat(hidden.get("accumulatedMeters").asLong()).isEqualTo(12_000);
        assertThat(hidden.get("ageBand").isNull()).as("나이 비공개").isTrue();
        assertThat(hidden.get("gender").isNull()).as("성별 비공개").isTrue();
        assertThat(hidden.get("ageBandVisibility").isNull()).as("남의 공개 설정은 싣지 않는다").isTrue();
        assertThat(hidden.get("genderVisibility").isNull()).isTrue();
        assertThat(hidden.get("level").isInt()).as("레벨은 늘 공개").isTrue();
        assertThat(hidden.get("joinedAt").isTextual()).isTrue();

        JsonNode shown = find(members, leader.memberId());
        assertThat(shown.get("role").asText()).isEqualTo("LEADER");
        assertThat(shown.get("accumulatedMeters").asLong()).isZero();
        assertThat(shown.get("ageBand").asText()).isEqualTo(ageBand(1990, year));
        assertThat(shown.get("gender").asText()).isEqualTo("FEMALE");
        assertThat(shown.get("ageBandVisibility").isNull()).isTrue();
    }

    @Test
    @DisplayName("크루원 목록 응답 끝에 faceKey·shownGender(설계 5.31 A): 고른 얼굴 키, 안 골랐으면 null, 성별 비공개는 대체 성별")
    void members_carryFaceKeyAndShownGender() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        long crewId = createCrew(leader, "FREE");
        call(a, post("/api/v1/crews/join"), Map.of("crewId", crewId))
                .andExpect(jsonPath("$.status").value("JOINED"));
        profile(leader, 1990, "FEMALE", "PUBLIC", "PUBLIC");
        profile(a, 1995, "MALE", "PUBLIC", "PRIVATE");
        call(leader, post("/api/v1/avatar/default"), Map.of("characterId", "FOX"))
                .andExpect(status().isOk());

        JsonNode members =
                json(call(a, get("/api/v1/crews/" + crewId + "/members"), null)).get("members");

        JsonNode withFace = find(members, leader.memberId());
        assertThat(withFace.get("faceKey").asText()).isEqualTo("default:FOX");
        assertThat(withFace.get("shownGender").asText()).isEqualTo("FEMALE");
        JsonNode noFace = find(members, a.memberId());
        assertThat(noFace.has("faceKey")).as("키는 항상 있고 값만 null").isTrue();
        assertThat(noFace.get("faceKey").isNull()).isTrue();
        assertThat(noFace.get("shownGender").asText())
                .as("성별 비공개는 회원 ID로 정한 대체 성별(본인이 봐도 같다)")
                .isEqualTo(ShownGender.fallbackOf(a.memberId()).name());
        List<String> keys = new ArrayList<>();
        noFace.fieldNames().forEachRemaining(keys::add);
        assertThat(keys.subList(keys.size() - 3, keys.size()))
                .as("새 필드는 맨 끝에 더한다")
                .containsExactly("genderVisibility", "faceKey", "shownGender");
    }

    @Test
    @DisplayName("가입 신청 목록: 신청자 레벨, 공개한 나이대·성별만")
    void joinRequests_carryApplicantProfile() throws Exception {
        Session leader = signUp();
        Session applicant = signUp();
        long crewId = createCrew(leader, "APPROVAL");
        profile(applicant, 1998, "MALE", "PUBLIC", "PRIVATE");
        call(applicant, post("/api/v1/crews/join"), Map.of("crewId", crewId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        JsonNode request =
                json(call(leader, get("/api/v1/crews/" + crewId + "/join-requests"), null))
                        .get("requests")
                        .get(0);
        assertThat(request.get("memberId").asLong()).isEqualTo(applicant.memberId());
        assertThat(request.get("level").isInt()).isTrue();
        assertThat(request.get("ageBand").asText())
                .isEqualTo(ageBand(1998, LocalDate.now(clock).getYear()));
        assertThat(request.get("gender").isNull()).as("성별 비공개").isTrue();
        assertThat(request.has("tier")).isTrue();
    }

    @Test
    @DisplayName("지난 목표: 이번 주 앞의 목표 있는 주만 최신순, 100% 초과, 참여 인원, before 커서 페이지, 권한·입력 오류")
    void goalHistory_listsPastGoalWeeks() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        Session outsider = signUp();
        long crewId = createCrew(leader, "FREE");
        call(a, post("/api/v1/crews/join"), Map.of("crewId", crewId));
        LocalDate monday = thisMonday();
        LocalDate w1 = monday.minusWeeks(1);
        LocalDate w2 = monday.minusWeeks(2);
        LocalDate w3 = monday.minusWeeks(3);
        LocalDate w4 = monday.minusWeeks(4);
        week(crewId, monday, 5_000L, 1_000, false); // 이번 주는 뺀다
        week(crewId, w1, 50_000L, 61_500, true);
        week(crewId, w2, 80_000L, 72_000, false);
        week(crewId, w3, null, 4_000, false); // 목표 없는 주는 뺀다
        week(crewId, w4, 10_000L, 0, false);
        week(crewId + 100_000, w1, 1_000L, 0, false); // 다른 크루
        contribute(crewId, w1, a.memberId(), 30_000);
        contribute(crewId, w1, a.memberId(), 1_500);
        contribute(crewId, w1, leader.memberId(), 30_000);
        contribute(crewId, w1, null, 0); // 0 m 기여는 참여가 아니다
        contribute(crewId, w2, null, 72_000); // 탈퇴로 익명이 된 기여는 한 명

        JsonNode page1 =
                json(
                        call(
                                        a,
                                        get("/api/v1/crews/" + crewId + "/goals/history")
                                                .param("size", "2"),
                                        null)
                                .andExpect(status().isOk()));
        JsonNode goals = page1.get("goals");
        assertThat(goals.findValuesAsText("weekStart"))
                .containsExactly(w1.toString(), w2.toString());
        JsonNode first = goals.get(0);
        assertThat(first.get("goalMeters").asLong()).isEqualTo(50_000);
        assertThat(first.get("totalMeters").asLong()).isEqualTo(61_500);
        assertThat(first.get("achieved").asBoolean()).isTrue();
        assertThat(first.get("progressPercent").asInt()).as("100% 초과 허용(C2)").isEqualTo(123);
        assertThat(first.get("participantCount").asInt()).isEqualTo(2);
        LocalDate thursday = w1.plusDays(3);
        assertThat(first.get("month").asInt()).isEqualTo(thursday.getMonthValue());
        assertThat(first.get("weekOfMonth").asInt())
                .isEqualTo((thursday.getDayOfMonth() - 1) / 7 + 1);
        JsonNode second = goals.get(1);
        assertThat(second.get("achieved").asBoolean()).isFalse();
        assertThat(second.get("progressPercent").asInt()).isEqualTo(90);
        assertThat(second.get("participantCount").asInt()).isEqualTo(1);
        assertThat(page1.get("nextBefore").asText()).isEqualTo(w2.toString());

        JsonNode page2 =
                json(
                        call(
                                a,
                                get("/api/v1/crews/" + crewId + "/goals/history")
                                        .param("size", "2")
                                        .param("before", page1.get("nextBefore").asText()),
                                null));
        assertThat(page2.get("goals").findValuesAsText("weekStart")).containsExactly(w4.toString());
        assertThat(page2.get("goals").get(0).get("progressPercent").asInt()).isZero();
        assertThat(page2.get("goals").get(0).get("participantCount").asInt()).isZero();
        assertThat(page2.get("nextBefore").isNull()).isTrue();

        JsonNode all = json(call(leader, get("/api/v1/crews/" + crewId + "/goals/history"), null));
        assertThat(all.get("goals")).as("기본 20개").hasSize(3);
        assertThat(all.get("nextBefore").isNull()).isTrue();

        call(outsider, get("/api/v1/crews/" + crewId + "/goals/history"), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        call(a, get("/api/v1/crews/" + crewId + "/goals/history").param("size", "0"), null)
                .andExpect(status().isBadRequest());
        call(a, get("/api/v1/crews/" + crewId + "/goals/history").param("size", "101"), null)
                .andExpect(status().isBadRequest());
        call(a, get("/api/v1/crews/" + crewId + "/goals/history").param("before", "10월"), null)
                .andExpect(status().isBadRequest());
        call(a, get("/api/v1/crews/987654321/goals/history"), null)
                .andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));
    }
}
