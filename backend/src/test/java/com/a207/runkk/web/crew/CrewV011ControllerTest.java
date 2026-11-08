package com.a207.runkk.web.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.crew.JpegSamples;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * v0.11 크루 엔드포인트(부록 F F.2.1·F.2.2, T-33 일부): 운영진 지정·해제, 공지·고정, 투표·응답, 대면 모임·참석, 게시글
 * multipart·앨범·사진, 공동 목표, 목록 kinds·pinnedOnly, 시각 OffsetDateTime(D-114). 인증 필터를 거치고 test 프로필(H2 +
 * 내장 Redis)에서 돈다.
 */
@DisplayName("web.crew v0.11 크루 엔드포인트")
class CrewV011ControllerTest extends WebTestSupport {

    private static final AtomicInteger CREWS = new AtomicInteger();
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

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

    private record Crew(long id, Session l, Session m, Session r1, Session o) {}

    private Crew crew() throws Exception {
        Session l = signUp();
        Session m = signUp();
        Session r1 = signUp();
        Map<String, Object> form = new HashMap<>();
        form.put("name", "새크루" + CREWS.incrementAndGet() + "번");
        form.put("intro", "함께 달려요");
        form.put("tags", List.of("WEEKDAY_EVENING"));
        form.put("visibility", "PUBLIC");
        form.put("joinPolicy", "FREE");
        JsonNode created =
                json(call(l, post("/api/v1/crews"), form).andExpect(status().isCreated()));
        assertThat(OffsetDateTime.parse(created.get("inviteCodeExpiresAt").asText()).getOffset())
                .as("D-114 오프셋 포함")
                .hasToString("+09:00");
        long id = created.get("id").asLong();
        for (Session s : List.of(m, r1)) {
            call(s, post("/api/v1/crews/join"), Map.of("crewId", id))
                    .andExpect(jsonPath("$.status").value("JOINED"));
        }
        call(l, put("/api/v1/crews/" + id + "/managers/" + m.memberId()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(m.memberId()))
                .andExpect(jsonPath("$.role").value("MANAGER"));
        return new Crew(id, l, m, r1, signUp());
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

    @Test
    @DisplayName("TC-6801·TC-6802·TC-6806: 운영진 지정·해제는 크루장만, 상한·대상 오류는 409, 운영진의 크루 정보 변경은 403")
    void managers() throws Exception {
        Crew c = crew();
        call(c.m(), put("/api/v1/crews/" + c.id() + "/managers/" + c.r1().memberId()), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        call(c.l(), put("/api/v1/crews/" + c.id() + "/managers/" + c.o().memberId()), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TARGET_INVALID"));
        call(c.l(), put("/api/v1/crews/" + c.id() + "/managers/" + c.l().memberId()), null)
                .andExpect(jsonPath("$.code").value("TARGET_INVALID"));
        call(c.l(), delete("/api/v1/crews/" + c.id() + "/managers/" + c.r1().memberId()), null)
                .andExpect(jsonPath("$.code").value("TARGET_INVALID"));
        call(c.l(), delete("/api/v1/crews/" + c.id() + "/managers/" + c.m().memberId()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
        call(c.m(), patch(c.id()), Map.of("intro", "운영진이 바꿈")).andExpect(status().isForbidden());
        call(c.l(), get("/api/v1/crews/" + c.id() + "/members"), null)
                .andExpect(jsonPath("$.members[?(@.role == 'MANAGER')]").isEmpty());
    }

    private static MockHttpServletRequestBuilder patch(long crewId) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                "/api/v1/crews/" + crewId);
    }

    @Test
    @DisplayName("TC-6807·TC-6810·TC-6820: 공지는 운영진 이상, 고정·pinnedOnly·kinds 목록, canDelete·notice 항목")
    void noticesAndList() throws Exception {
        Crew c = crew();
        call(
                        c.r1(),
                        post("/api/v1/crews/" + c.id() + "/posts"),
                        Map.of("kind", "NOTICE", "body", "공지"))
                .andExpect(status().isForbidden());
        long notice = write(c.m(), c.id(), Map.of("kind", "NOTICE", "body", "가".repeat(1000)));
        call(
                        c.m(),
                        post("/api/v1/crews/" + c.id() + "/posts"),
                        Map.of("kind", "NOTICE", "body", "가".repeat(1001)))
                .andExpect(jsonPath("$.code").value("BODY_TOO_LONG"));
        call(c.r1(), put("/api/v1/posts/" + notice + "/pin"), Map.of("pinned", true))
                .andExpect(status().isForbidden());
        call(c.l(), put("/api/v1/posts/" + notice + "/pin"), Map.of("pinned", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));
        long cheer = write(c.r1(), c.id(), Map.of("kind", "CHEER", "body", "화이팅"));
        call(c.l(), put("/api/v1/posts/" + cheer + "/pin"), Map.of("pinned", true))
                .andExpect(jsonPath("$.code").value("TARGET_INVALID"));

        call(c.r1(), get("/api/v1/crews/" + c.id() + "/posts").param("pinnedOnly", "true"), null)
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.posts[0].notice.pinned").value(true))
                .andExpect(jsonPath("$.posts[0].canDelete").value(false));
        call(c.r1(), get("/api/v1/crews/" + c.id() + "/posts").param("kinds", "NOTICE,CHEER"), null)
                .andExpect(jsonPath("$.posts.length()").value(2));
        call(c.r1(), get("/api/v1/crews/" + c.id() + "/posts").param("kind", "CHEER"), null)
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.posts[0].canDelete").value(true));
        JsonNode list = json(call(c.m(), get("/api/v1/crews/" + c.id() + "/posts"), null));
        assertThat(list.get("posts").findValuesAsText("canDelete")).containsOnly("true");
        assertThat(
                        OffsetDateTime.parse(list.get("posts").get(0).get("writtenAt").asText())
                                .getOffset())
                .hasToString("+09:00");
        call(c.m(), delete("/api/v1/posts/" + cheer), null).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("TC-6811·TC-6812·TC-6882: 투표 입력 범위는 400, 마감 지난 투표 409, 찬반 선택지 자동, 응답 변경·결과(익명 아님)")
    void polls() throws Exception {
        Crew c = crew();
        String closes = inHours(24);
        call(
                        c.r1(),
                        post("/api/v1/crews/" + c.id() + "/posts"),
                        Map.of(
                                "kind",
                                "POLL",
                                "body",
                                "어디서?",
                                "pollMethod",
                                "CHOICES",
                                "options",
                                List.of("가"),
                                "closesAt",
                                closes))
                .andExpect(status().isBadRequest());
        call(
                        c.r1(),
                        post("/api/v1/crews/" + c.id() + "/posts"),
                        Map.of(
                                "kind",
                                "POLL",
                                "body",
                                "찬반?",
                                "pollMethod",
                                "YES_NO",
                                "options",
                                List.of("가", "나"),
                                "closesAt",
                                closes))
                .andExpect(status().isBadRequest());
        call(
                        c.r1(),
                        post("/api/v1/crews/" + c.id() + "/posts"),
                        Map.of(
                                "kind",
                                "POLL",
                                "body",
                                "지난?",
                                "pollMethod",
                                "YES_NO",
                                "closesAt",
                                inHours(-1)))
                .andExpect(jsonPath("$.code").value("POLL_INVALID"));
        long yesNo =
                write(
                        c.r1(),
                        c.id(),
                        Map.of(
                                "kind",
                                "POLL",
                                "body",
                                "모일까요?",
                                "pollMethod",
                                "YES_NO",
                                "closesAt",
                                closes));
        call(c.m(), get("/api/v1/posts/" + yesNo + "/poll"), null)
                .andExpect(jsonPath("$.options[0].label").value("찬성"))
                .andExpect(jsonPath("$.options[1].label").value("반대"));
        call(c.m(), put("/api/v1/posts/" + yesNo + "/poll-response"), Map.of("optionNo", 1))
                .andExpect(jsonPath("$.myOptionNo").value(1));
        call(c.m(), put("/api/v1/posts/" + yesNo + "/poll-response"), Map.of("optionNo", 2))
                .andExpect(jsonPath("$.myOptionNo").value(2))
                .andExpect(jsonPath("$.options[1].count").value(1))
                .andExpect(jsonPath("$.options[1].respondents[0].memberId").value(c.m().memberId()))
                .andExpect(jsonPath("$.options[0].count").value(0));
        call(c.m(), put("/api/v1/posts/" + yesNo + "/poll-response"), Map.of("optionNo", 9))
                .andExpect(jsonPath("$.code").value("POLL_INVALID"));
        call(c.o(), put("/api/v1/posts/" + yesNo + "/poll-response"), Map.of("optionNo", 1))
                .andExpect(status().isForbidden());
        call(c.r1(), get("/api/v1/crews/" + c.id() + "/posts").param("kinds", "POLL"), null)
                .andExpect(jsonPath("$.posts[0].poll.method").value("YES_NO"))
                .andExpect(jsonPath("$.posts[0].poll.closed").value(false))
                .andExpect(jsonPath("$.posts[0].poll.responseCount").value(1));
    }

    @Test
    @DisplayName("D-143: 이모지 공지 1,000자·투표 질문 200자·선택지 40자·장소명 50자를 저장하고, 공지 1,001자는 BODY_TOO_LONG")
    void emojiTexts_countCodePointsAndFitColumns() throws Exception {
        Crew c = crew();
        String emoji = "\uD83C\uDFC3";
        long notice = write(c.m(), c.id(), Map.of("kind", "NOTICE", "body", emoji.repeat(1000)));
        assertThat(notice).isPositive();
        call(
                        c.m(),
                        post("/api/v1/crews/" + c.id() + "/posts"),
                        Map.of("kind", "NOTICE", "body", emoji.repeat(1001)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BODY_TOO_LONG"));
        write(
                c.r1(),
                c.id(),
                Map.of(
                        "kind",
                        "POLL",
                        "body",
                        emoji.repeat(200),
                        "pollMethod",
                        "CHOICES",
                        "options",
                        List.of(emoji.repeat(40), "나"),
                        "closesAt",
                        inHours(24)));
        write(
                c.r1(),
                c.id(),
                Map.of(
                        "kind",
                        "MEETUP",
                        "body",
                        emoji.repeat(200),
                        "scheduledAt",
                        inHours(48),
                        "placeName",
                        emoji.repeat(50)));
    }

    @Test
    @DisplayName("TC-6814·TC-6815: 대면 모임 장소·정원 범위 400, 지난 일시 409, 작성자 자동 참석, 정원이 차면 SCHEDULE_FULL")
    void meetups() throws Exception {
        Crew c = crew();
        String at = inHours(48);
        call(
                        c.r1(),
                        post("/api/v1/crews/" + c.id() + "/posts"),
                        Map.of("kind", "MEETUP", "scheduledAt", at, "placeName", "가".repeat(51)))
                .andExpect(status().isBadRequest());
        call(
                        c.r1(),
                        post("/api/v1/crews/" + c.id() + "/posts"),
                        Map.of(
                                "kind",
                                "MEETUP",
                                "scheduledAt",
                                at,
                                "placeName",
                                "공원",
                                "capacity",
                                1))
                .andExpect(status().isBadRequest());
        call(
                        c.r1(),
                        post("/api/v1/crews/" + c.id() + "/posts"),
                        Map.of("kind", "MEETUP", "scheduledAt", inHours(-1), "placeName", "공원"))
                .andExpect(jsonPath("$.code").value("SCHEDULE_INVALID"));
        long meet =
                write(
                        c.r1(),
                        c.id(),
                        Map.of(
                                "kind",
                                "MEETUP",
                                "scheduledAt",
                                at,
                                "placeName",
                                "한강공원",
                                "capacity",
                                2));
        call(c.m(), put("/api/v1/posts/" + meet + "/attendance"), Map.of("answer", "GOING"))
                .andExpect(jsonPath("$.goingCount").value(2));
        call(c.l(), put("/api/v1/posts/" + meet + "/attendance"), Map.of("answer", "GOING"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_FULL"));
        call(c.l(), put("/api/v1/posts/" + meet + "/attendance"), Map.of("answer", "UNDECIDED"))
                .andExpect(jsonPath("$.undecidedCount").value(1));
        JsonNode item =
                json(call(
                                c.l(),
                                get("/api/v1/crews/" + c.id() + "/posts").param("kinds", "MEETUP"),
                                null))
                        .get("posts")
                        .get(0)
                        .get("meetup");
        assertThat(item.get("placeName").asText()).isEqualTo("한강공원");
        assertThat(item.get("capacity").asInt()).isEqualTo(2);
        assertThat(item.get("goingCount").asInt()).isEqualTo(2);
        assertThat(item.get("myAnswer").asText()).isEqualTo("UNDECIDED");
        assertThat(OffsetDateTime.parse(item.get("scheduledAt").asText()).toInstant())
                .isEqualTo(OffsetDateTime.parse(at).toInstant());
    }

    private MockMultipartHttpServletRequestBuilder board(long crewId) {
        return multipart("/api/v1/crews/" + crewId + "/board-posts");
    }

    private static MockMultipartFile photo(byte[] bytes) {
        return new MockMultipartFile("photos", "p.jpg", "image/jpeg", bytes);
    }

    @Test
    @DisplayName("TC-6817·TC-6818·TC-6819: 게시글 multipart 저장(위치 제거)·오류 코드, 앨범·사진(크루원만, private 캐시)")
    void boardAlbumPhoto() throws Exception {
        Crew c = crew();
        byte[] gps = JpegSamples.withLocation(new byte[256]);
        long id =
                json(mvc.perform(
                                        as(
                                                c.r1(),
                                                board(c.id())
                                                        .file(photo(gps))
                                                        .file(photo(gps))
                                                        .param("body", "사진 두 장")))
                                .andExpect(status().isCreated()))
                        .get("id")
                        .asLong();
        mvc.perform(as(c.r1(), board(c.id()).param("body", " ")))
                .andExpect(jsonPath("$.code").value("POST_EMPTY"));
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0};
        mvc.perform(as(c.r1(), board(c.id()).file(photo(png))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        byte[] small = JpegSamples.clean(new byte[8]);
        MockMultipartHttpServletRequestBuilder six = board(c.id());
        for (int i = 0; i < 6; i++) {
            six.file(photo(small));
        }
        mvc.perform(as(c.r1(), six)).andExpect(jsonPath("$.code").value("PHOTO_LIMIT"));
        mvc.perform(as(c.o(), board(c.id()).param("body", "밖에서")))
                .andExpect(status().isForbidden());

        JsonNode album = json(call(c.r1(), get("/api/v1/crews/" + c.id() + "/album"), null));
        assertThat(album.get("photos")).hasSize(2);
        String url = album.get("photos").get(0).get("url").asText();
        assertThat(url).startsWith("/api/v1/crew-photos/");
        byte[] stored =
                mvc.perform(as(c.m(), get(url)))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                        .andExpect(header().string("Cache-Control", "private, max-age=300"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(JpegSamples.count(stored, "GPS")).isZero();
        assertThat(JpegSamples.count(stored, "Exif")).isZero();
        mvc.perform(as(c.o(), get(url))).andExpect(status().isForbidden());
        call(c.r1(), get("/api/v1/crews/" + c.id() + "/posts").param("kinds", "BOARD"), null)
                .andExpect(jsonPath("$.posts[0].photos.length()").value(2))
                .andExpect(
                        jsonPath("$.posts[0].photos[0].url")
                                .value(org.hamcrest.Matchers.startsWith("/api/v1/crew-photos/")));

        call(c.r1(), delete("/api/v1/posts/" + id), null).andExpect(status().isNoContent());
        mvc.perform(as(c.m(), get(url)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        assertThat(
                        json(call(c.r1(), get("/api/v1/crews/" + c.id() + "/album"), null))
                                .get("photos"))
                .isEmpty();
    }

    @Test
    @DisplayName("TC-6741·TC-6743: 공동 목표는 운영진 이상, 1km 미만 409, 정한 즉시 달성하면 achievedAt·축하 카드")
    void goal() throws Exception {
        Crew c = crew();
        call(c.r1(), put("/api/v1/crews/" + c.id() + "/goal"), Map.of("goalMeters", 30_000))
                .andExpect(status().isForbidden());
        call(c.l(), put("/api/v1/crews/" + c.id() + "/goal"), Map.of("goalMeters", 999))
                .andExpect(jsonPath("$.code").value("GOAL_INVALID"));
        call(c.m(), put("/api/v1/crews/" + c.id() + "/goal"), Map.of("goalMeters", 30_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalMeters").value(30_000))
                .andExpect(jsonPath("$.goalProgressPercent").value(0))
                .andExpect(jsonPath("$.achievedAt").doesNotExist())
                .andExpect(jsonPath("$.celebration").doesNotExist());
        JsonNode race = json(call(c.r1(), get("/api/v1/crews/" + c.id() + "/race"), null));
        assertThat(race.get("goalMeters").asLong()).isEqualTo(30_000);
        assertThat(OffsetDateTime.parse(race.get("closesAt").asText()).getOffset())
                .hasToString("+09:00");
    }
}
