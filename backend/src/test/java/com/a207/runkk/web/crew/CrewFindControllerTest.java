package com.a207.runkk.web.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.common.Distance;
import com.a207.runkk.crew.CrewRunService;
import com.a207.runkk.crew.JpegSamples;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 피그마 맞춤 W4(S15P31A207-295, contract-W4.md): 크루 상세 통계·최근 크루런, 검색 전체 개수, 24시간 가입 제한 시각, 초대 코드 미리보기와
 * 시도 제한, 크루 로고 업로드·조회, 크루명 2~12자. test 프로필(H2 + 내장 Redis)에서 인증 필터를 거쳐 돈다.
 */
@DisplayName("web.crew 크루 찾기·상세·초대·만들기(W4)")
class CrewFindControllerTest extends WebTestSupport {

    private static final AtomicInteger CREWS = new AtomicInteger();
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired CrewRunService crewRunService;
    @Autowired RunUploadService runUploadService;
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

    private JsonNode createCrew(Session leader, String name, String visibility, String policy)
            throws Exception {
        return createCrew(leader, name, visibility, policy, null);
    }

    private JsonNode createCrew(
            Session leader, String name, String visibility, String policy, String logoUrl)
            throws Exception {
        Map<String, Object> form = new HashMap<>();
        form.put("name", name);
        form.put("intro", "새벽에 함께 달려요");
        form.put("tags", List.of("WEEKDAY_DAWN"));
        form.put("visibility", visibility);
        form.put("joinPolicy", policy);
        if (logoUrl != null) {
            form.put("logoUrl", logoUrl);
        }
        return json(call(leader, post("/api/v1/crews"), form).andExpect(status().isCreated()));
    }

    private static String uniqueName() {
        return "W4찾기" + CREWS.incrementAndGet();
    }

    private static String at(LocalDateTime t) {
        return t.atZone(SEOUL).toOffsetDateTime().toString();
    }

    @Test
    @DisplayName("크루 상세: 개설일·누적 거리·누적 크루런·최근 크루런(크루 밖도 보임), 마친 크루런만 센다")
    void detail_hasStatsAndRecentCrewRuns() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        Session outsider = signUp();
        long crewId = createCrew(leader, uniqueName(), "PUBLIC", "FREE").get("id").asLong();
        call(a, post("/api/v1/crews/join"), Map.of("crewId", crewId))
                .andExpect(jsonPath("$.status").value("JOINED"));

        JsonNode fresh = json(call(outsider, get("/api/v1/crews/" + crewId), null));
        assertThat(OffsetDateTime.parse(fresh.get("createdAt").asText()).toLocalDate())
                .isEqualTo(LocalDateTime.now().toLocalDate());
        assertThat(fresh.get("totalDistanceMeters").asLong()).isZero();
        assertThat(fresh.get("crewRunCount").asInt()).isZero();
        assertThat(fresh.get("recentCrewRuns")).isEmpty();
        assertThat(fresh.get("rejoinAvailableAt").isNull()).isTrue();
        assertThat(fresh.get("lastWeekDistanceMeters").asLong()).isZero();

        LocalDateTime soon = LocalDateTime.now().plusMinutes(20).truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime scheduledAt = soon.withMinute(soon.getMinute() / 5 * 5); // 5분 단위(W6)
        long run = departedCrewRun(leader, a, crewId, scheduledAt);
        LocalDateTime start = scheduledAt.plusSeconds(3);
        call(
                        a,
                        post("/api/v1/crew-runs/" + run + "/quit"),
                        Map.of("distanceMeters", 980, "elapsedSeconds", 300))
                .andExpect(status().isNoContent());
        uploadCrewRun(leader.memberId(), run, start, 1_100);

        JsonNode running = json(call(outsider, get("/api/v1/crews/" + crewId), null));
        assertThat(running.get("crewRunCount").asInt()).as("진행 중은 세지 않는다").isZero();

        crewRunService.tick(start.plusMinutes(46));
        JsonNode result = json(call(a, get("/api/v1/crew-runs/" + run + "/result"), null));
        assertThat(result.get("status").asText()).isEqualTo("FINISHED");
        long raceTotal =
                json(call(a, get("/api/v1/crews/" + crewId + "/race"), null))
                        .get("totalMeters")
                        .asLong();

        JsonNode detail = json(call(outsider, get("/api/v1/crews/" + crewId), null));
        assertThat(detail.get("myRole").isNull()).isTrue();
        assertThat(detail.get("crewRunCount").asInt()).isEqualTo(1);
        assertThat(detail.get("totalDistanceMeters").asLong())
                .as("누적 크루 거리 = 주간 레이스 합계의 합")
                .isEqualTo(raceTotal)
                .isPositive();
        JsonNode recent = detail.get("recentCrewRuns");
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).get("postId").asLong()).isEqualTo(run);
        assertThat(OffsetDateTime.parse(recent.get(0).get("scheduledAt").asText()).toInstant())
                .isEqualTo(scheduledAt.atZone(SEOUL).toInstant());
        assertThat(recent.get(0).get("participantCount").asInt()).isEqualTo(2);
        assertThat(recent.get(0).get("totalDistanceMeters").asLong())
                .as("결과 화면과 같은 거리 합")
                .isEqualTo(result.get("totalDistanceMeters").asLong())
                .isEqualTo(2_080);
        assertThat(recent.get(0).has("participants")).as("개인 정보 없음").isFalse();
    }

    @Test
    @DisplayName("크루 검색: totalCount는 같은 조건의 전체 수이고 커서·크기와 상관없다")
    void search_hasTotalCount() throws Exception {
        String token = "Q" + UUID.randomUUID().toString().substring(0, 4);
        for (int i = 0; i < 3; i++) {
            createCrew(signUp(), token + "크루" + i, "PUBLIC", "FREE");
        }
        Session viewer = signUp();
        createCrew(signUp(), token + "비공개", "PRIVATE", "FREE");

        JsonNode first =
                json(
                        call(
                                viewer,
                                get("/api/v1/crews").param("keyword", token).param("size", "2"),
                                null));
        assertThat(first.get("crews")).hasSize(2);
        assertThat(first.get("totalCount").asLong()).as("비공개 크루는 세지 않는다").isEqualTo(3);
        JsonNode second =
                json(
                        call(
                                viewer,
                                get("/api/v1/crews")
                                        .param("keyword", token)
                                        .param("size", "2")
                                        .param("cursor", first.get("nextCursor").asText()),
                                null));
        assertThat(second.get("crews")).hasSize(1);
        assertThat(second.get("totalCount").asLong()).isEqualTo(3);
        assertThat(
                        json(call(
                                        viewer,
                                        get("/api/v1/crews").param("keyword", token + "없음"),
                                        null))
                                .get("totalCount")
                                .asLong())
                .isZero();
    }

    @Test
    @DisplayName("24시간 가입 제한: 나온 사람에게 /crews/me와 상세가 가입 가능 시각을 주고, 크루원·제한 없는 사람은 null")
    void rejoinAvailableAt_isExposed() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        long crewId = createCrew(leader, uniqueName(), "PUBLIC", "FREE").get("id").asLong();
        long other = createCrew(signUp(), uniqueName(), "PUBLIC", "FREE").get("id").asLong();
        assertThat(json(call(a, get("/api/v1/crews/me"), null)).get("rejoinAvailableAt").isNull())
                .isTrue();
        call(a, post("/api/v1/crews/join"), Map.of("crewId", crewId));
        LocalDateTime before = LocalDateTime.now();
        call(a, post("/api/v1/crews/" + crewId + "/leave"), null).andExpect(status().isNoContent());

        JsonNode me = json(call(a, get("/api/v1/crews/me"), null));
        assertThat(me.get("crew").isNull()).isTrue();
        OffsetDateTime available = OffsetDateTime.parse(me.get("rejoinAvailableAt").asText());
        assertThat(available.toInstant())
                .isAfterOrEqualTo(before.plusHours(24).atZone(SEOUL).toInstant().minusSeconds(1))
                .isBefore(before.plusHours(24).plusMinutes(1).atZone(SEOUL).toInstant());
        JsonNode otherDetail = json(call(a, get("/api/v1/crews/" + other), null));
        assertThat(OffsetDateTime.parse(otherDetail.get("rejoinAvailableAt").asText()).toInstant())
                .isEqualTo(available.toInstant());
        call(a, post("/api/v1/crews/join"), Map.of("crewId", other))
                .andExpect(jsonPath("$.code").value("COOLDOWN"));
        assertThat(
                        json(call(leader, get("/api/v1/crews/" + crewId), null))
                                .get("rejoinAvailableAt")
                                .isNull())
                .isTrue();
        assertThat(
                        json(call(leader, get("/api/v1/crews/me"), null))
                                .get("rejoinAvailableAt")
                                .isNull())
                .isTrue();
    }

    @Test
    @DisplayName("초대 코드 미리보기: 비공개 크루도 요약을 주고 가입하지 않는다, 틀린 코드는 INVALID_CODE, 빈 값은 400")
    void invitePreview_thenJoin() throws Exception {
        Session leader = signUp();
        Session b = signUp();
        String name = uniqueName();
        String code = createCrew(leader, name, "PRIVATE", "APPROVAL").get("inviteCode").asText();

        JsonNode preview =
                json(
                        call(
                                        b,
                                        post("/api/v1/crews/invite-preview"),
                                        Map.of("inviteCode", " " + code.toLowerCase() + " "))
                                .andExpect(status().isOk()));
        assertThat(preview.get("name").asText()).isEqualTo(name);
        assertThat(preview.get("intro").asText()).isEqualTo("새벽에 함께 달려요");
        assertThat(preview.get("tags").get(0).asText()).isEqualTo("WEEKDAY_DAWN");
        assertThat(preview.get("visibility").asText()).isEqualTo("PRIVATE");
        assertThat(preview.get("joinPolicy").asText()).isEqualTo("APPROVAL");
        assertThat(preview.get("headcount").asInt()).isEqualTo(1);
        assertThat(preview.get("createdAt").asText()).isNotBlank();
        assertThat(preview.has("inviteCode")).isFalse();
        assertThat(json(call(b, get("/api/v1/crews/me"), null)).get("crew").isNull())
                .as("미리보기는 가입시키지 않는다")
                .isTrue();

        String wrong = code.equals("ZZZZZZ") ? "YYYYYY" : "ZZZZZZ";
        call(b, post("/api/v1/crews/invite-preview"), Map.of("inviteCode", wrong))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"));
        call(b, post("/api/v1/crews/invite-preview"), Map.of("inviteCode", " "))
                .andExpect(status().isBadRequest());
        call(b, post("/api/v1/crews/invite-preview"), Map.of()).andExpect(status().isBadRequest());

        call(b, post("/api/v1/crews/join"), Map.of("inviteCode", code))
                .andExpect(jsonPath("$.status").value("JOINED"))
                .andExpect(jsonPath("$.crewId").value(preview.get("crewId").asLong()));
    }

    @Test
    @DisplayName("초대 코드 미리보기 대입 가드: 10분에 틀린 코드 5번이면 맞는 코드도 429, 가입도 함께 막힌다")
    void invitePreview_isRateLimited() throws Exception {
        Session leader = signUp();
        Session c = signUp();
        String code = createCrew(leader, uniqueName(), "PUBLIC", "FREE").get("inviteCode").asText();
        String wrong = code.equals("ZZZZZZ") ? "YYYYYY" : "ZZZZZZ";
        for (int i = 0; i < 5; i++) {
            call(c, post("/api/v1/crews/invite-preview"), Map.of("inviteCode", wrong))
                    .andExpect(jsonPath("$.code").value("INVALID_CODE"));
        }
        call(c, post("/api/v1/crews/invite-preview"), Map.of("inviteCode", code))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        call(c, post("/api/v1/crews/join"), Map.of("inviteCode", code))
                .andExpect(status().isTooManyRequests());
    }

    private static MockMultipartFile logo(byte[] bytes) {
        return new MockMultipartFile("logo", "logo.jpg", "image/jpeg", bytes);
    }

    @Test
    @DisplayName("크루 로고: JPEG 업로드(위치 제거) → 경로를 크루 만들기에 싣고 누구나 바이트를 읽는다, 형식·크기·없는 ID 오류")
    void logo_uploadAndUse() throws Exception {
        Session leader = signUp();
        Session outsider = signUp();
        JsonNode uploaded =
                json(
                        mvc.perform(
                                        as(
                                                leader,
                                                multipart("/api/v1/crew-logos")
                                                        .file(
                                                                logo(
                                                                        JpegSamples.withLocation(
                                                                                new byte[256])))))
                                .andExpect(status().isCreated()));
        long logoId = uploaded.get("logoId").asLong();
        String url = uploaded.get("logoUrl").asText();
        assertThat(url).isEqualTo("/api/v1/crew-logos/" + logoId);

        byte[] stored =
                mvc.perform(as(outsider, get(url)))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                        .andExpect(header().string("Cache-Control", "private, max-age=86400"))
                        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(JpegSamples.count(stored, "GPS")).isZero();
        assertThat(JpegSamples.count(stored, "Exif")).isZero();

        long crewId = createCrew(leader, uniqueName(), "PUBLIC", "FREE", url).get("id").asLong();
        call(outsider, get("/api/v1/crews/" + crewId), null)
                .andExpect(jsonPath("$.logoUrl").value(url));

        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0};
        mvc.perform(as(leader, multipart("/api/v1/crew-logos").file(logo(png))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        mvc.perform(
                        as(
                                leader,
                                multipart("/api/v1/crew-logos")
                                        .file(logo(JpegSamples.ofSize(1_048_577)))))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("TOO_LARGE"));
        mvc.perform(as(leader, multipart("/api/v1/crew-logos"))).andExpect(status().isBadRequest());
        mvc.perform(as(leader, get("/api/v1/crew-logos/" + (logoId + 100_000))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREW_LOGO_NOT_FOUND"));
    }

    @Test
    @DisplayName("크루 로고 주소: https나 업로드 경로만 받는다")
    void logoUrl_mustBeHttpsOrUploadedPath() throws Exception {
        for (String bad :
                List.of(
                        "http://example.com/a.jpg",
                        "/api/v1/crew-photos/1",
                        "/api/v1/crew-logos/abc",
                        "javascript:alert(1)")) {
            Map<String, Object> form = new HashMap<>();
            form.put("name", uniqueName());
            form.put("tags", List.of("WEEKDAY_DAWN"));
            form.put("visibility", "PUBLIC");
            form.put("joinPolicy", "FREE");
            form.put("logoUrl", bad);
            call(signUp(), post("/api/v1/crews"), form).andExpect(status().isBadRequest());
        }
        createCrew(signUp(), uniqueName(), "PUBLIC", "FREE", "https://cdn.example.com/a.jpg");
    }

    @Test
    @DisplayName("크루 로고 업로드는 회원별 1시간에 20장까지(21번째 429)")
    void logoUpload_isRateLimited() throws Exception {
        Session s = signUp();
        byte[] jpeg = JpegSamples.clean(new byte[16]);
        for (int i = 0; i < 20; i++) {
            mvc.perform(as(s, multipart("/api/v1/crew-logos").file(logo(jpeg))))
                    .andExpect(status().isCreated());
        }
        mvc.perform(as(s, multipart("/api/v1/crew-logos").file(logo(jpeg))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        mvc.perform(as(signUp(), multipart("/api/v1/crew-logos").file(logo(jpeg))))
                .andExpect(status().isCreated());
    }

    private String uploadLogo(Session s) throws Exception {
        return json(mvc.perform(
                                as(
                                        s,
                                        multipart("/api/v1/crew-logos")
                                                .file(logo(JpegSamples.clean(new byte[16])))))
                        .andExpect(status().isCreated()))
                .get("logoUrl")
                .asText();
    }

    @Test
    @DisplayName("로고 남용(코드 리뷰 M2): 크루에 연결되지 않은 로고는 회원당 최근 3장만 남고, 크루가 쓰는 로고는 지우지 않는다")
    void unlinkedLogos_keepNewestThree() throws Exception {
        Session s = signUp();
        Session viewer = signUp();
        String linked = uploadLogo(s);
        createCrew(s, uniqueName(), "PUBLIC", "FREE", linked);
        String first = uploadLogo(s);
        String second = uploadLogo(s);
        String third = uploadLogo(s);
        mvc.perform(as(viewer, get(first))).andExpect(status().isOk());

        String fourth = uploadLogo(s);

        mvc.perform(as(viewer, get(first)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREW_LOGO_NOT_FOUND"));
        for (String kept : List.of(second, third, fourth, linked)) {
            mvc.perform(as(viewer, get(kept))).andExpect(status().isOk());
        }
        uploadLogo(viewer); // 다른 회원의 미연결 로고는 세지 않는다
        mvc.perform(as(viewer, get(second))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로고 주인 확인(코드 리뷰 M2): 남이 올린 로고·없는 로고 경로로 만들기·변경은 400, 크루에 이미 연결된 로고는 그대로 보낼 수 있다")
    void logoUrl_mustBeOwnOrAlreadyLinked() throws Exception {
        Session owner = signUp();
        Session other = signUp();
        String othersLogo = uploadLogo(other);
        String missing = "/api/v1/crew-logos/999999999999";
        for (String bad : List.of(othersLogo, missing)) {
            Map<String, Object> form = new HashMap<>();
            form.put("name", uniqueName());
            form.put("tags", List.of("WEEKDAY_DAWN"));
            form.put("visibility", "PUBLIC");
            form.put("joinPolicy", "FREE");
            form.put("logoUrl", bad);
            call(owner, post("/api/v1/crews"), form)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }

        String own = uploadLogo(owner);
        long crewId = createCrew(owner, uniqueName(), "PUBLIC", "FREE", own).get("id").asLong();
        call(owner, patch("/api/v1/crews/" + crewId), Map.of("logoUrl", othersLogo))
                .andExpect(status().isBadRequest());

        call(other, post("/api/v1/crews/join"), Map.of("crewId", crewId))
                .andExpect(jsonPath("$.status").value("JOINED"));
        call(
                        owner,
                        post("/api/v1/crews/" + crewId + "/leader"),
                        Map.of("memberId", other.memberId()))
                .andExpect(status().is2xxSuccessful());
        call(other, patch("/api/v1/crews/" + crewId), Map.of("logoUrl", own, "intro", "새 크루장"))
                .andExpect(status().isNoContent());
        call(other, patch("/api/v1/crews/" + crewId), Map.of("logoUrl", othersLogo))
                .andExpect(status().isNoContent());
        call(other, get("/api/v1/crews/" + crewId), null)
                .andExpect(jsonPath("$.logoUrl").value(othersLogo));
    }

    @Test
    @DisplayName("크루명 2~12자: 만들기·이름 변경은 13자 거부, 이미 있는 13자 이상 크루는 이름을 그대로 두고 다른 항목을 고칠 수 있다")
    void crewName_isTwoToTwelve() throws Exception {
        Session leader = signUp();
        Map<String, Object> form = new HashMap<>();
        form.put("name", "열세글자크루이름입니다요!");
        form.put("tags", List.of("WEEKDAY_DAWN"));
        form.put("visibility", "PUBLIC");
        form.put("joinPolicy", "FREE");
        assertThat((String) form.get("name")).hasSize(13);
        call(leader, post("/api/v1/crews"), form)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREW_NAME_INVALID"));

        String twelve = "W4" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long crewId = createCrew(leader, twelve, "PUBLIC", "FREE").get("id").asLong();
        String legacy = "옛긴이름" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        assertThat(legacy).hasSize(16);
        jdbc.update("update crew set name = ? where id = ?", legacy, crewId);

        call(leader, get("/api/v1/crews/" + crewId), null)
                .andExpect(jsonPath("$.name").value(legacy));
        call(leader, patch("/api/v1/crews/" + crewId), Map.of("name", legacy, "intro", "바꿈"))
                .andExpect(status().isNoContent());
        call(leader, patch("/api/v1/crews/" + crewId), Map.of("intro", "또 바꿈"))
                .andExpect(status().isNoContent());
        call(leader, patch("/api/v1/crews/" + crewId), Map.of("name", legacy + "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREW_NAME_INVALID"));
        call(leader, get("/api/v1/crews/" + crewId), null)
                .andExpect(jsonPath("$.name").value(legacy))
                .andExpect(jsonPath("$.intro").value("또 바꿈"));
        call(leader, patch("/api/v1/crews/" + crewId), Map.of("name", twelve))
                .andExpect(status().isNoContent());
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
}
