package com.a207.runkk.web.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.common.Distance;
import com.a207.runkk.crew.CrewRunService;
import com.a207.runkk.crew.MembershipService;
import com.a207.runkk.crew.WeeklyRaceService;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunUpload;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 5.7.1 크루 엔드포인트 전부(/crews·/join-requests·/posts·/crew-runs). 인증 필터를 거치고 test 프로필(H2 + 내장 Redis)에서
 * 돈다.
 */
@DisplayName("web.crew 크루 엔드포인트")
class CrewControllerTest extends WebTestSupport {

    private static final AtomicInteger CREWS = new AtomicInteger();

    @Autowired CrewRunService crewRunService;
    @Autowired MembershipService membershipService;
    @Autowired WeeklyRaceService weeklyRaceService;
    @Autowired RunUploadService runUploadService;

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
        form.put("name", "크루" + CREWS.incrementAndGet() + "번");
        form.put("intro", "새벽에 함께 달려요");
        form.put("tags", List.of("WEEKDAY_EVENING", "WEEKEND_MORNING"));
        form.put("visibility", "PUBLIC");
        form.put("joinPolicy", policy);
        JsonNode created =
                json(call(leader, post("/api/v1/crews"), form).andExpect(status().isCreated()));
        assertThat(created.get("inviteCode").asText()).matches("[A-Z0-9]{6}");
        return created.get("id").asLong();
    }

    @Test
    @DisplayName("생성·목록·승인제 가입(승인·거절·취소)·코드 가입·크루원·강제 퇴장 쿨타임·양도·정보 변경·삭제")
    void crewLifecycle() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        Session b = signUp();
        Session c = signUp();
        long crewId = createCrew(leader, "APPROVAL");

        call(leader, post("/api/v1/crews"), Map.of("name", "또만들기"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_IN_CREW"));
        JsonNode list = json(call(a, get("/api/v1/crews").param("keyword", "크루"), null));
        assertThat(list.get("crews").findValuesAsText("crewId")).contains(String.valueOf(crewId));
        assertThat(list.has("recommendations")).as("추천 삭제(FR-118)").isFalse();
        assertThat(list.get("crews").get(0).has("tagMatches")).isFalse();
        call(a, get("/api/v1/crews").param("tags", "WEEKDAY_EVENING,WEEKDAY_DAWN"), null) // P2
                .andExpect(status().isOk());
        call(a, get("/api/v1/crews").param("tags", "NOPE"), null)
                .andExpect(status().isBadRequest());
        // 2차 L13: 태그 AND를 SQL로 거른다(",태그," 단위라 WEEKDAY_EVENING이 다른 태그의 일부로 맞지 않는다)
        String id = String.valueOf(crewId);
        assertThat(
                        json(call(
                                        a,
                                        get("/api/v1/crews")
                                                .param("tags", "WEEKEND_MORNING,WEEKDAY_EVENING"),
                                        null))
                                .get("crews")
                                .findValuesAsText("crewId"))
                .contains(id);
        assertThat(
                        json(call(
                                        a,
                                        get("/api/v1/crews")
                                                .param("tags", "WEEKDAY_EVENING,WEEKDAY_DAWN"),
                                        null))
                                .get("crews")
                                .findValuesAsText("crewId"))
                .doesNotContain(id);
        // L13: 검색어의 %·_는 글자 그대로 찾는다(모든 크루와 맞지 않는다)
        assertThat(json(call(a, get("/api/v1/crews").param("keyword", "%_%"), null)).get("crews"))
                .isEmpty();
        // 추천 삭제(FR-118): GET은 받지 않는다(같은 경로 모양의 다른 메소드 매핑이 있어 405)
        call(a, get("/api/v1/crews/recommendations"), null)
                .andExpect(status().isMethodNotAllowed());

        long requestA =
                json(call(a, post("/api/v1/crews/join"), Map.of("crewId", crewId)))
                        .get("requestId")
                        .asLong();
        call(a, get("/api/v1/crews/" + crewId + "/join-requests"), null)
                .andExpect(status().isForbidden());
        call(leader, get("/api/v1/crews/" + crewId + "/join-requests"), null)
                .andExpect(jsonPath("$.requests[0].requestId").value(requestA));
        call(leader, post("/api/v1/join-requests/" + requestA + "/approve"), null)
                .andExpect(jsonPath("$.status").value("APPROVED"));
        call(leader, post("/api/v1/join-requests/" + requestA + "/approve"), null)
                .andExpect(jsonPath("$.code").value("REQUEST_DECIDED"));

        String code =
                json(call(leader, post("/api/v1/crews/" + crewId + "/invite-code"), null))
                        .get("inviteCode")
                        .asText();
        call(b, post("/api/v1/crews/join"), Map.of("inviteCode", code.toLowerCase()))
                .andExpect(jsonPath("$.status").value("JOINED"));

        long requestC =
                json(call(c, post("/api/v1/crews/join"), Map.of("crewId", crewId)))
                        .get("requestId")
                        .asLong();
        call(c, post("/api/v1/crews/join"), Map.of("crewId", crewId))
                .andExpect(jsonPath("$.code").value("ALREADY_REQUESTED"));
        call(leader, post("/api/v1/join-requests/" + requestC + "/reject"), null)
                .andExpect(jsonPath("$.status").value("REJECTED"));
        long again =
                json(call(c, post("/api/v1/crews/join"), Map.of("crewId", crewId)))
                        .get("requestId")
                        .asLong();
        call(c, delete("/api/v1/join-requests/" + again), null)
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        call(a, patch("/api/v1/crews/" + crewId + "/me"), Map.of("intro", "반가워요"))
                .andExpect(status().isNoContent());
        JsonNode members = json(call(a, get("/api/v1/crews/" + crewId + "/members"), null));
        assertThat(members.get("members")).hasSize(3);
        assertThat(members.get("members").findValuesAsText("crewIntro")).contains("반가워요");
        call(c, get("/api/v1/crews/" + crewId + "/members"), null)
                .andExpect(status().isForbidden());

        call(leader, post("/api/v1/crews/" + crewId + "/members/" + b.memberId() + "/kick"), null)
                .andExpect(status().isNoContent());
        call(b, post("/api/v1/crews/join"), Map.of("inviteCode", code))
                .andExpect(jsonPath("$.code").value("COOLDOWN"));
        call(leader, post("/api/v1/crews/" + crewId + "/leave"), null)
                .andExpect(jsonPath("$.code").value("LEADER_MUST_TRANSFER"));

        call(leader, post("/api/v1/crews/" + crewId + "/leader"), Map.of("memberId", a.memberId()))
                .andExpect(status().isNoContent());
        call(leader, patch("/api/v1/crews/" + crewId), Map.of("intro", "바뀜"))
                .andExpect(status().isForbidden());
        call(a, patch("/api/v1/crews/" + crewId), Map.of("intro", "바뀐 소개", "joinPolicy", "FREE"))
                .andExpect(status().isNoContent());
        call(leader, post("/api/v1/crews/" + crewId + "/leave"), null)
                .andExpect(status().isNoContent());

        call(a, get("/api/v1/crews/" + crewId + "/ranking").param("period", "MONTHLY"), null)
                .andExpect(jsonPath("$.period").value("MONTHLY"))
                .andExpect(jsonPath("$.entries[0].rank").value(1));
        call(a, get("/api/v1/crews/" + crewId + "/race"), null)
                .andExpect(jsonPath("$.totalMeters").value(0));

        call(a, delete("/api/v1/crews/" + crewId), null).andExpect(status().isNoContent());
        call(a, get("/api/v1/crews/" + crewId + "/members"), null)
                .andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));
        createCrew(a, "FREE");
    }

    private void uploadCrewRun(long memberId, long postId, LocalDateTime start, long meters) {
        int seconds = (int) (meters * 6 / 10);
        List<LocationPoint> points =
                RunFixtures.straightLine(start, (double) meters / seconds, seconds);
        runUploadService.upload(
                memberId,
                new RunUploadCommand(
                        new RunUpload(
                                UUID.randomUUID().toString(),
                                start,
                                start.plusSeconds(seconds),
                                Distance.ofMeters(meters),
                                Duration.ofSeconds(seconds),
                                RunMode.CREW_RUN,
                                CardKind.CREW_RUN,
                                null,
                                postId,
                                points.size(),
                                points,
                                List.of(),
                                List.of())));
    }

    @Test
    @DisplayName("게시판(응원·모집·투표·강제 삭제)과 크루런(대기실 → 출발 → 러닝 연결·도착 → 종료 → 결과·주간 거리)")
    void postsAndCrewRun() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        long crewId = createCrew(leader, "FREE");
        call(a, post("/api/v1/crews/join"), Map.of("crewId", crewId))
                .andExpect(jsonPath("$.status").value("JOINED"));

        long cheer =
                json(call(
                                a,
                                post("/api/v1/crews/" + crewId + "/posts"),
                                Map.of("kind", "CHEER", "body", "화이팅")))
                        .get("id")
                        .asLong();
        LocalDateTime scheduledAt = slotIn20Minutes();
        long run =
                json(call(
                                leader,
                                post("/api/v1/crews/" + crewId + "/posts"),
                                Map.of(
                                        "kind",
                                        "CREW_RUN",
                                        "body",
                                        "3km 같이 달려요",
                                        "distanceMeters",
                                        3000,
                                        "meetAt",
                                        scheduledAt
                                                .atZone(java.time.ZoneId.of("Asia/Seoul"))
                                                .toOffsetDateTime()
                                                .toString())))
                        .get("id")
                        .asLong();
        call(a, put("/api/v1/posts/" + run + "/attendance"), Map.of("answer", "GOING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goingCount").value(2));
        call(a, put("/api/v1/posts/" + run + "/vote"), Map.of("answer", "GOING")) // F.2.4 삭제
                .andExpect(status().isNotFound());
        JsonNode posts = json(call(a, get("/api/v1/crews/" + crewId + "/posts"), null));
        assertThat(posts.get("posts")).hasSize(2);
        assertThat(posts.get("posts").get(0).get("crewRun").get("goingCount").asInt()).isEqualTo(2);
        call(leader, delete("/api/v1/posts/" + cheer), null).andExpect(status().isNoContent());
        call(a, get("/api/v1/crews/" + crewId + "/posts").param("kind", "CHEER"), null)
                .andExpect(jsonPath("$.posts").isEmpty());
        call(a, get("/api/v1/crews/" + crewId + "/posts").param("kind", "NOPE"), null) // P3
                .andExpect(status().isBadRequest());

        call(a, post("/api/v1/avatar/default"), Map.of("characterId", "FOX"))
                .andExpect(status().isOk());
        call(a, post("/api/v1/crew-runs/" + run + "/lobby/enter"), null)
                .andExpect(jsonPath("$.status").value("LOBBY_OPEN"))
                // 크루원 아바타(S15P31A207-236, 계약 crewrun-sum v1.2): 얼굴 키(프로필과 같은 값)와 표시 성별(D-327)
                .andExpect(
                        jsonPath("$.participants[?(@.memberId == " + a.memberId() + ")].faceKey")
                                .value(org.hamcrest.Matchers.contains("default:FOX")))
                .andExpect(
                        jsonPath("$.participants[*].shownGender")
                                .value(
                                        org.hamcrest.Matchers.everyItem(
                                                org.hamcrest.Matchers.oneOf("MALE", "FEMALE"))))
                // 대기실 참가자에 look이 실린다(설계 5.31 B [임시:D-성-254]): 기존 faceKey·shownGender는 그대로 둔다
                .andExpect(
                        jsonPath(
                                        "$.participants[?(@.memberId == "
                                                + a.memberId()
                                                + ")].look.faceKey")
                                .value(org.hamcrest.Matchers.contains("default:FOX")))
                .andExpect(
                        jsonPath("$.participants[*].look.gender")
                                .value(
                                        org.hamcrest.Matchers.everyItem(
                                                org.hamcrest.Matchers.oneOf("MALE", "FEMALE"))))
                .andExpect(
                        jsonPath("$.participants[*].look.bodyVariant")
                                .value(
                                        org.hamcrest.Matchers.everyItem(
                                                org.hamcrest.Matchers.oneOf(0, 1, 2))))
                .andExpect(
                        jsonPath("$.participants[*].look.equipped")
                                .value(
                                        org.hamcrest.Matchers.everyItem(
                                                org.hamcrest.Matchers.instanceOf(
                                                        java.util.Map.class))));
        call(leader, post("/api/v1/crew-runs/" + run + "/lobby/enter"), null);
        call(a, post("/api/v1/crew-runs/" + run + "/lobby/ready"), null);
        call(leader, post("/api/v1/crew-runs/" + run + "/lobby/leave"), null);
        call(leader, post("/api/v1/crew-runs/" + run + "/lobby/enter"), null);
        call(leader, post("/api/v1/crew-runs/" + run + "/lobby/ready"), null)
                .andExpect(jsonPath("$.participants[?(@.ready == true)]").isNotEmpty())
                .andExpect(jsonPath("$.distanceMeters").value(3000))
                .andExpect(
                        jsonPath("$.participants[?(@.me == true)].memberId")
                                .value(org.hamcrest.Matchers.contains((int) leader.memberId())));
        JsonNode listed = json(call(a, get("/api/v1/crews/" + crewId + "/posts"), null));
        JsonNode crewRunPost = listed.get("posts").get(0).get("crewRun");
        assertThat(crewRunPost.get("distanceMeters").asLong()).isEqualTo(3000);
        assertThat(crewRunPost.get("myAnswer").asText()).isEqualTo("GOING");
        assertThat(crewRunPost.get("status").asText()).isEqualTo("LOBBY_OPEN");

        crewRunService.tick(scheduledAt);
        LocalDateTime start = scheduledAt.plusSeconds(3);
        call(a, post("/api/v1/crew-runs/" + run + "/pause"), Map.of("elapsedSeconds", 120))
                .andExpect(status().isNoContent());
        call(a, post("/api/v1/crew-runs/" + run + "/pause"), null)
                .andExpect(status().isNoContent());
        call(
                        a,
                        post("/api/v1/crew-runs/" + run + "/quit"),
                        Map.of("distanceMeters", -1, "elapsedSeconds", 10))
                .andExpect(status().isConflict()) // 계약 crewrun-sum v1.1 4절(MR !91 BR-C2)
                .andExpect(jsonPath("$.code").value("CREW_RUN_INVALID"));
        call(
                        a,
                        post("/api/v1/crew-runs/" + run + "/quit"),
                        Map.of("distanceMeters", 980, "elapsedSeconds", 300))
                .andExpect(status().isNoContent());
        call(a, post("/api/v1/crew-runs/" + run + "/pause"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_PARTICIPANT"));
        uploadCrewRun(a.memberId(), run, start, 1_000);
        uploadCrewRun(leader.memberId(), run, start, 1_100);
        crewRunService.tick(start.plusMinutes(45)); // 3km × km당 15분

        JsonNode result = json(call(a, get("/api/v1/crew-runs/" + run + "/result"), null));
        assertThat(result.get("status").asText()).isEqualTo("FINISHED");
        assertThat(result.get("title").asText()).isEqualTo("3km 같이 달려요");
        assertThat(result.get("distanceMeters").asLong()).isEqualTo(3_000);
        assertThat(result.get("startedAt").asText()).isNotBlank();
        assertThat(result.get("totalDistanceMeters").asLong()).as("연결된 기록 거리 합").isEqualTo(2_100);
        assertThat(result.get("raceContributionMeters").asLong()).isEqualTo(2_100);
        assertThat(result.get("participants").findValuesAsText("memberId"))
                .containsExactly(String.valueOf(leader.memberId()), String.valueOf(a.memberId()));
        assertThat(result.get("participants").findValuesAsText("distanceMeters"))
                .containsExactly("1100", "1000");
        assertThat(result.get("participants").findValues("ended")).allMatch(JsonNode::asBoolean);
        assertThat(result.get("participants").findValues("me").stream().filter(JsonNode::asBoolean))
                .hasSize(1);
        assertThat(result.get("myRecordId").asText()).isNotBlank();
        assertThat(result.has("finishers")).isFalse();
        JsonNode leaderLine = result.get("participants").get(0);
        JsonNode aLine = result.get("participants").get(1);
        assertThat(aLine.get("faceKey").asText()).isEqualTo("default:FOX");
        assertThat(leaderLine.hasNonNull("faceKey")).as("얼굴을 고르지 않았으면 null").isFalse();
        assertThat(result.get("participants").findValuesAsText("shownGender"))
                .allMatch(g -> g.equals("MALE") || g.equals("FEMALE"));
        // 종료 뒤 다시 끝내기는 아무 일 없음(재시도 안전)
        call(a, post("/api/v1/crew-runs/" + run + "/quit"), null).andExpect(status().isNoContent());
        call(a, post("/api/v1/crew-runs/" + run + "/pause"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_PARTICIPANT"));
        call(a, get("/api/v1/crews/" + crewId + "/race"), null)
                .andExpect(jsonPath("$.totalMeters").value(2_100))
                .andExpect(
                        jsonPath("$.contributors[?(@.me == true)].memberId")
                                .value(org.hamcrest.Matchers.contains((int) a.memberId())));
        assertThat(weeklyRaceService.reconcileAll(LocalDateTime.now())).isZero();
        assertThat(membershipService.expireRequests(LocalDateTime.now())).isZero();
    }

    /** 크루런 모집글 하나를 만들고 두 사람이 대기실에 들어가 준비한 뒤 출발시킨다. 모집글 ID. */
    private long departedCrewRun(Session leader, Session a, long crewId, LocalDateTime scheduledAt)
            throws Exception {
        long run =
                json(call(
                                leader,
                                post("/api/v1/crews/" + crewId + "/posts"),
                                Map.of(
                                        "kind",
                                        "CREW_RUN",
                                        "body",
                                        "3km 같이 달려요",
                                        "distanceMeters",
                                        3000,
                                        "meetAt",
                                        at(scheduledAt))))
                        .get("id")
                        .asLong();
        call(a, put("/api/v1/posts/" + run + "/attendance"), Map.of("answer", "GOING"));
        for (Session s : List.of(leader, a)) {
            call(s, post("/api/v1/crew-runs/" + run + "/lobby/enter"), null);
            call(s, post("/api/v1/crew-runs/" + run + "/lobby/ready"), null);
        }
        crewRunService.tick(scheduledAt);
        return run;
    }

    /** 20분 안쪽의 5분 단위 시각(크루런 작성 일시 규칙 W6: 5분 단위, 대기실은 30분 전에 열린다). */
    private static LocalDateTime slotIn20Minutes() {
        LocalDateTime t =
                LocalDateTime.now()
                        .plusMinutes(20)
                        .truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        return t.withMinute(t.getMinute() / 5 * 5);
    }

    private static String at(LocalDateTime t) {
        return t.atZone(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime().toString();
    }

    /** POST /runs 본문(앱 RunUploadRequest와 같은 모양). */
    private static Map<String, Object> runBody(RunUpload u) {
        Map<String, Object> body = new HashMap<>();
        body.put("recordId", u.recordId());
        body.put("startedAt", at(u.startedAt()));
        body.put("endedAt", at(u.endedAt()));
        body.put("distanceMeters", u.distance().meters());
        body.put("elapsedMillis", u.elapsed().toMillis());
        body.put("mode", u.mode().name());
        body.put("cardKind", u.cardKind().name());
        body.put("crewRunPostId", u.crewRunPostId());
        body.put("pointCount", u.points().size());
        body.put(
                "points",
                u.points().stream()
                        .map(
                                p ->
                                        Map.of(
                                                "measuredAt", at(p.measuredAt()),
                                                "latitude", p.point().latitude(),
                                                "longitude", p.point().longitude(),
                                                "accuracyMeters", 5))
                        .toList());
        body.put(
                "pauses",
                u.pauses().stream()
                        .map(p -> Map.of("from", at(p.from()), "to", at(p.to())))
                        .toList());
        return body;
    }

    @Test
    @DisplayName(
            "BR-1: 한 번 멈춘 크루런 기록도 POST /runs가 받고(200 STORED) 참여에 이어져 결과·주간 레이스에 운동 시간·거리로 들어간다")
    void pausedCrewRunUpload_isStoredAndLinked() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        long crewId = createCrew(leader, "FREE");
        call(a, post("/api/v1/crews/join"), Map.of("crewId", crewId));
        LocalDateTime scheduledAt = slotIn20Minutes();
        long run = departedCrewRun(leader, a, crewId, scheduledAt);
        LocalDateTime start = scheduledAt.plusSeconds(3);

        call(a, post("/api/v1/crew-runs/" + run + "/pause"), Map.of("elapsedSeconds", 450))
                .andExpect(status().isNoContent());
        RunUpload paused =
                RunFixtures.pausedCrewRun(
                        UUID.randomUUID().toString(),
                        start,
                        run,
                        3_000,
                        900,
                        new RunFixtures.Pause(450, 120));
        call(a, post("/api/v1/runs"), runBody(paused))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STORED"))
                .andExpect(jsonPath("$.valid").value(true));
        call(
                        a,
                        post("/api/v1/crew-runs/" + run + "/quit"),
                        Map.of("distanceMeters", 3_000, "elapsedSeconds", 900))
                .andExpect(status().isNoContent());
        uploadCrewRun(leader.memberId(), run, start, 3_000);
        crewRunService.tick(start.plusMinutes(46));

        JsonNode result = json(call(a, get("/api/v1/crew-runs/" + run + "/result"), null));
        assertThat(result.get("myRecordId").asText())
                .as("참여 연결(linkRun)")
                .isEqualTo(paused.recordId());
        assertThat(result.get("totalDistanceMeters").asLong()).isEqualTo(6_000);
        assertThat(result.get("raceContributionMeters").asLong()).isEqualTo(6_000);
        JsonNode mine = null;
        for (JsonNode p : result.get("participants")) {
            if (p.get("me").asBoolean()) {
                mine = p;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("distanceMeters").asLong()).isEqualTo(3_000);
        assertThat(mine.get("elapsedSeconds").asLong()).as("정지 2분을 뺀 운동 시간").isEqualTo(900);
        call(a, get("/api/v1/crews/" + crewId + "/race"), null)
                .andExpect(jsonPath("$.totalMeters").value(6_000));
        call(a, get("/api/v1/runs/" + paused.recordId()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pausedSeconds").value(120));
    }

    @Test
    @DisplayName("내 크루(GET /crews/me)·크루 단건(GET /crews/{id})·크루원 본인 표시·대기 중 신청")
    void myCrewAndDetail() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        Session outsider = signUp();
        call(a, get("/api/v1/crews/me"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crew").doesNotExist())
                .andExpect(jsonPath("$.pendingRequests").isEmpty());
        long crewId = createCrew(leader, "APPROVAL");

        long requestId =
                json(call(a, post("/api/v1/crews/join"), Map.of("crewId", crewId)))
                        .get("requestId")
                        .asLong();
        call(a, get("/api/v1/crews/me"), null)
                .andExpect(jsonPath("$.crew").doesNotExist())
                .andExpect(jsonPath("$.pendingRequests[0].requestId").value(requestId))
                .andExpect(jsonPath("$.pendingRequests[0].crewId").value(crewId))
                .andExpect(jsonPath("$.pendingRequests[0].crewName").isNotEmpty());
        call(leader, post("/api/v1/join-requests/" + requestId + "/approve"), null);

        JsonNode mine = json(call(leader, get("/api/v1/crews/me"), null));
        assertThat(mine.get("crew").get("crewId").asLong()).isEqualTo(crewId);
        assertThat(mine.get("crew").get("myRole").asText()).isEqualTo("LEADER");
        assertThat(mine.get("crew").get("inviteCode").asText()).matches("[A-Z0-9]{6}");
        assertThat(mine.get("crew").get("headcount").asInt()).isEqualTo(2);
        assertThat(mine.get("crew").get("joinPolicy").asText()).isEqualTo("APPROVAL");
        assertThat(mine.get("crew").get("tags")).hasSize(2);

        JsonNode asMember = json(call(a, get("/api/v1/crews/" + crewId), null));
        assertThat(asMember.get("myRole").asText()).isEqualTo("MEMBER");
        assertThat(asMember.get("inviteCode").asText())
                .as("크루원도 초대 코드를 본다(F-18b)")
                .isEqualTo(mine.get("crew").get("inviteCode").asText());
        assertThat(asMember.get("leaderId").asLong()).isEqualTo(leader.memberId());
        assertThat(asMember.get("leaderName").asText()).isNotBlank();
        JsonNode asOutsider = json(call(outsider, get("/api/v1/crews/" + crewId), null));
        assertThat(asOutsider.get("myRole").isNull()).isTrue();
        assertThat(asOutsider.get("weeklyDistanceMeters").asLong()).isZero();
        assertThat(asOutsider.get("lastWeekDistanceMeters").asLong()).isZero();

        call(leader, patch("/api/v1/crews/" + crewId), Map.of("visibility", "PRIVATE"))
                .andExpect(status().isNoContent());
        call(outsider, get("/api/v1/crews/" + crewId), null)
                .andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));
        call(a, get("/api/v1/crews/" + crewId), null).andExpect(status().isOk());
        call(a, get("/api/v1/crews/987654321"), null)
                .andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));

        JsonNode members = json(call(a, get("/api/v1/crews/" + crewId + "/members"), null));
        for (JsonNode m : members.get("members")) {
            assertThat(m.get("me").asBoolean())
                    .isEqualTo(m.get("memberId").asLong() == a.memberId());
        }
    }

    @Test
    @DisplayName("초대 코드 10분에 5번 틀리면 429 TOO_MANY_ATTEMPTS, 크루 목록·게시글 페이지 나누기(cursor·size)")
    void inviteCodeLimit_andPaging() throws Exception {
        Session leader = signUp();
        long crewId = createCrew(leader, "FREE");
        Session other = signUp();
        createCrew(other, "FREE");
        Session guesser = signUp();
        String code =
                json(call(leader, post("/api/v1/crews/" + crewId + "/invite-code"), null))
                        .get("inviteCode")
                        .asText();
        for (int i = 0; i < 5; i++) {
            call(guesser, post("/api/v1/crews/join"), Map.of("inviteCode", "ZZZZZ" + i))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_CODE"));
        }
        call(guesser, post("/api/v1/crews/join"), Map.of("inviteCode", code))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        call(guesser, post("/api/v1/crews/join"), Map.of("crewId", crewId))
                .andExpect(jsonPath("$.status").value("JOINED"));

        JsonNode firstCrews = json(call(guesser, get("/api/v1/crews").param("size", "1"), null));
        assertThat(firstCrews.get("crews")).hasSize(1);
        long crewCursor = firstCrews.get("nextCursor").asLong();
        JsonNode nextCrews =
                json(
                        call(
                                guesser,
                                get("/api/v1/crews")
                                        .param("size", "1")
                                        .param("cursor", String.valueOf(crewCursor)),
                                null));
        assertThat(nextCrews.get("crews").get(0).get("crewId").asLong()).isLessThan(crewCursor);
        assertThat(json(call(guesser, get("/api/v1/crews"), null)).get("nextCursor").isNull())
                .isTrue();

        for (int i = 0; i < 3; i++) {
            call(
                            leader,
                            post("/api/v1/crews/" + crewId + "/posts"),
                            Map.of("kind", "CHEER", "body", "글" + i))
                    .andExpect(status().isCreated());
        }
        JsonNode page1 =
                json(
                        call(
                                guesser,
                                get("/api/v1/crews/" + crewId + "/posts").param("size", "2"),
                                null));
        assertThat(page1.get("posts").findValuesAsText("body")).containsExactly("글2", "글1");
        JsonNode page2 =
                json(
                        call(
                                guesser,
                                get("/api/v1/crews/" + crewId + "/posts")
                                        .param("size", "2")
                                        .param("cursor", page1.get("nextCursor").asText()),
                                null));
        assertThat(page2.get("posts").findValuesAsText("body")).containsExactly("글0");
        assertThat(page2.get("nextCursor").isNull()).isTrue();
    }
}
