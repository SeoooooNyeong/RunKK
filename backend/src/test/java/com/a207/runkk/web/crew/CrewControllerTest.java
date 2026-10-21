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
        form.put("tags", List.of("SOCIAL_RUN", "MORNING"));
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
        assertThat(list.get("recommendations")).isNotNull();
        call(a, get("/api/v1/crews").param("tags", "SOCIAL_RUN,SOLO_RUN"), null) // P2
                .andExpect(status().isOk());
        call(a, get("/api/v1/crews").param("tags", "NOPE"), null)
                .andExpect(status().isBadRequest());
        // L13: 검색어의 %·_는 글자 그대로 찾는다(모든 크루와 맞지 않는다)
        assertThat(json(call(a, get("/api/v1/crews").param("keyword", "%_%"), null)).get("crews"))
                .isEmpty();
        call(a, get("/api/v1/crews/recommendations"), null).andExpect(status().isOk());

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
        LocalDateTime scheduledAt = LocalDateTime.now().plusMinutes(20).withNano(0);
        long run =
                json(call(
                                leader,
                                post("/api/v1/crews/" + crewId + "/posts"),
                                Map.of(
                                        "kind",
                                        "CREW_RUN",
                                        "body",
                                        "1km 같이 달려요",
                                        "distanceMeters",
                                        1000,
                                        "meetAt",
                                        scheduledAt.toString())))
                        .get("id")
                        .asLong();
        call(a, put("/api/v1/posts/" + run + "/vote"), Map.of("answer", "GOING"))
                .andExpect(status().isNoContent());
        JsonNode posts = json(call(a, get("/api/v1/crews/" + crewId + "/posts"), null));
        assertThat(posts.get("posts")).hasSize(2);
        assertThat(posts.get("posts").get(0).get("crewRun").get("goingCount").asInt()).isEqualTo(2);
        call(leader, delete("/api/v1/posts/" + cheer), null).andExpect(status().isNoContent());
        call(a, get("/api/v1/crews/" + crewId + "/posts").param("kind", "CHEER"), null)
                .andExpect(jsonPath("$.posts").isEmpty());
        call(a, get("/api/v1/crews/" + crewId + "/posts").param("kind", "NOPE"), null) // P3
                .andExpect(status().isBadRequest());

        call(a, post("/api/v1/crew-runs/" + run + "/lobby/enter"), null)
                .andExpect(jsonPath("$.status").value("LOBBY_OPEN"));
        call(leader, post("/api/v1/crew-runs/" + run + "/lobby/enter"), null);
        call(a, post("/api/v1/crew-runs/" + run + "/lobby/ready"), null);
        call(leader, post("/api/v1/crew-runs/" + run + "/lobby/leave"), null);
        call(leader, post("/api/v1/crew-runs/" + run + "/lobby/enter"), null);
        call(leader, post("/api/v1/crew-runs/" + run + "/lobby/ready"), null)
                .andExpect(jsonPath("$.participants[?(@.ready == true)]").isNotEmpty())
                .andExpect(jsonPath("$.distanceMeters").value(1000))
                .andExpect(
                        jsonPath("$.participants[?(@.me == true)].memberId")
                                .value(org.hamcrest.Matchers.contains((int) leader.memberId())));
        JsonNode listed = json(call(a, get("/api/v1/crews/" + crewId + "/posts"), null));
        JsonNode crewRunPost = listed.get("posts").get(0).get("crewRun");
        assertThat(crewRunPost.get("distanceMeters").asLong()).isEqualTo(1000);
        assertThat(crewRunPost.get("myAnswer").asText()).isEqualTo("GOING");
        assertThat(crewRunPost.get("status").asText()).isEqualTo("LOBBY_OPEN");

        crewRunService.tick(scheduledAt);
        LocalDateTime start = scheduledAt.plusSeconds(3);
        uploadCrewRun(a.memberId(), run, start, 1_000);
        uploadCrewRun(leader.memberId(), run, start, 1_100);
        crewRunService.tick(start.plusMinutes(15));

        JsonNode result = json(call(a, get("/api/v1/crew-runs/" + run + "/result"), null));
        assertThat(result.get("status").asText()).isEqualTo("FINISHED");
        assertThat(result.get("finisherCount").asInt()).isEqualTo(2);
        assertThat(result.get("finishers").get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(result.get("raceContributionMeters").asLong()).isEqualTo(2_100);
        assertThat(result.get("finishers").findValuesAsText("memberId"))
                .contains(String.valueOf(a.memberId()), String.valueOf(leader.memberId()));
        assertThat(result.get("finishers").findValues("me").stream().filter(JsonNode::asBoolean))
                .hasSize(1);
        call(a, post("/api/v1/crew-runs/" + run + "/quit"), null)
                .andExpect(jsonPath("$.code").value("NOT_PARTICIPANT"));
        call(a, get("/api/v1/crews/" + crewId + "/race"), null)
                .andExpect(jsonPath("$.totalMeters").value(2_100))
                .andExpect(
                        jsonPath("$.contributors[?(@.me == true)].memberId")
                                .value(org.hamcrest.Matchers.contains((int) a.memberId())));
        assertThat(weeklyRaceService.reconcileAll(LocalDateTime.now())).isZero();
        assertThat(membershipService.expireRequests(LocalDateTime.now())).isZero();
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
        assertThat(asMember.get("inviteCode").isNull()).isTrue();
        assertThat(asMember.get("leaderId").asLong()).isEqualTo(leader.memberId());
        assertThat(asMember.get("leaderName").asText()).isNotBlank();
        JsonNode asOutsider = json(call(outsider, get("/api/v1/crews/" + crewId), null));
        assertThat(asOutsider.get("myRole").isNull()).isTrue();
        assertThat(asOutsider.get("weeklyDistanceMeters").asLong()).isZero();

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
