package com.a207.runkk.web.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W6 크루 일정 엔드포인트(S15P31A207-297, contract-W6.md): 작성(제목·반복·거리·15일·5분), 상세, 수정(PATCH), 참석 두 상태, 지난
 * 크루런 연월. 인증 필터를 거치고 test 프로필(H2 + 내장 Redis)에서 돈다.
 */
@DisplayName("web.crew 크루 일정 엔드포인트(W6)")
class CrewScheduleControllerTest extends WebTestSupport {

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

    private record Crew(long id, Session l, Session r, Session o) {}

    private Crew crew() throws Exception {
        Session l = signUp();
        Session r = signUp();
        Map<String, Object> form = new HashMap<>();
        form.put("name", "일정크루" + CREWS.incrementAndGet() + "번");
        form.put("tags", List.of("WEEKDAY_EVENING"));
        form.put("visibility", "PUBLIC");
        form.put("joinPolicy", "FREE");
        long id =
                json(call(l, post("/api/v1/crews"), form).andExpect(status().isCreated()))
                        .get("id")
                        .asLong();
        call(r, post("/api/v1/crews/join"), Map.of("crewId", id))
                .andExpect(jsonPath("$.status").value("JOINED"));
        return new Crew(id, l, r, signUp());
    }

    /** 내일 같은 시각을 5분 단위로 내린 값. */
    private static OffsetDateTime tomorrow() {
        OffsetDateTime t = OffsetDateTime.now(SEOUL).plusDays(1).truncatedTo(ChronoUnit.MINUTES);
        return t.withMinute(t.getMinute() / 5 * 5);
    }

    private static Map<String, Object> run(
            String title, Object distance, Object at, String repeat) {
        Map<String, Object> body = new HashMap<>();
        body.put("kind", "CREW_RUN");
        body.put("title", title);
        body.put("body", "8번 출구");
        body.put("distanceMeters", distance);
        body.put("scheduledAt", at.toString());
        body.put("repeat", repeat);
        return body;
    }

    private ResultActions writeRun(Session s, long crewId, Map<String, Object> body)
            throws Exception {
        return call(s, post("/api/v1/crews/" + crewId + "/posts"), body);
    }

    private long created(ResultActions result) throws Exception {
        return json(result.andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    @DisplayName(
            "작성: 빈 제목·1km·5분 단위·15일 범위 위반은 CREW_RUN_INVALID, 제한 없음(null)·하프는 받고 제목 없는 옛 앱 본문은 첫 줄이"
                    + " 제목")
    void writeRules() throws Exception {
        Crew c = crew();
        OffsetDateTime at = tomorrow();
        created(writeRun(c.l(), c.id(), run("제한 없음", null, at, null)));
        created(writeRun(c.l(), c.id(), run("하프", 21097, at, "NONE")));
        for (Map<String, Object> bad :
                List.of(
                        run("1km", 1000, at, null),
                        run("2km", 2000, at, null),
                        run(" ", 5000, at, null),
                        run("7분", 5000, at.plusMinutes(2), null),
                        run("15일 뒤", 5000, at.plusDays(14), null))) {
            writeRun(c.l(), c.id(), bad)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CREW_RUN_INVALID"));
        }
        Map<String, Object> legacy = run("x", 5000, at, null);
        legacy.remove("title");
        legacy.put("body", "옛 앱 제목\n옛 앱 내용");
        long old = created(writeRun(c.l(), c.id(), legacy));
        call(c.l(), get("/api/v1/crew-runs/" + old), null)
                .andExpect(jsonPath("$.title").value("옛 앱 제목"))
                .andExpect(jsonPath("$.body").value("옛 앱 내용"));
        writeRun(c.l(), c.id(), run("반복값", 5000, at, "DAILY")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상세·수정: 응답 칸(제목·내용·거리 null·repeat·attendees·canEdit), 남의 수정 403, 필수 칸 없으면 400")
    void detailAndEdit() throws Exception {
        Crew c = crew();
        OffsetDateTime at = tomorrow();
        long id = created(writeRun(c.l(), c.id(), run("토요일 한강", 5000, at, "WEEKLY")));
        String path = "/api/v1/crew-runs/" + id;

        call(c.r(), get(path), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(id))
                .andExpect(jsonPath("$.crewId").value(c.id()))
                .andExpect(jsonPath("$.title").value("토요일 한강"))
                .andExpect(jsonPath("$.body").value("8번 출구"))
                .andExpect(jsonPath("$.distanceMeters").value(5000))
                .andExpect(jsonPath("$.status").value("VOTING"))
                .andExpect(jsonPath("$.goingCount").value(1))
                .andExpect(jsonPath("$.myAnswer").doesNotExist())
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.canDelete").value(false))
                .andExpect(jsonPath("$.repeat").value("WEEKLY"))
                .andExpect(jsonPath("$.repeatDayOfWeek").value(at.getDayOfWeek().name()))
                .andExpect(jsonPath("$.attendees[0].memberId").value(c.l().memberId()))
                .andExpect(jsonPath("$.attendees[0].author").value(true))
                .andExpect(jsonPath("$.attendees[0].me").value(false));
        JsonNode detail = json(call(c.l(), get(path), null));
        assertThat(OffsetDateTime.parse(detail.get("scheduledAt").asText()).toInstant())
                .isEqualTo(at.toInstant());
        assertThat(detail.get("canEdit").asBoolean()).isTrue();

        Map<String, Object> edit = new HashMap<>();
        edit.put("title", "토요일 한강 제한 없음");
        edit.put("body", null);
        edit.put("distanceMeters", null);
        edit.put("repeat", "NONE");
        call(c.r(), patch(path), edit)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        call(c.l(), patch(path), edit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("토요일 한강 제한 없음"))
                .andExpect(jsonPath("$.body").value(""))
                .andExpect(jsonPath("$.distanceMeters").doesNotExist())
                .andExpect(jsonPath("$.repeat").value("NONE"))
                .andExpect(jsonPath("$.repeatDayOfWeek").doesNotExist());
        call(c.l(), patch(path), Map.of("title", "반복 빠짐")).andExpect(status().isBadRequest());

        call(c.o(), get(path), null).andExpect(status().isForbidden());
        call(c.l(), get("/api/v1/crew-runs/999999"), null)
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));

        call(c.l(), get("/api/v1/crews/" + c.id() + "/posts?kinds=CREW_RUN"), null)
                .andExpect(jsonPath("$.posts[0].crewRun.title").value("토요일 한강 제한 없음"))
                .andExpect(jsonPath("$.posts[0].crewRun.distanceMeters").doesNotExist())
                .andExpect(jsonPath("$.posts[0].body").value(""));
    }

    @Test
    @DisplayName("참석하기 PUT GOING, 참석 취소 DELETE는 응답을 거둔다(myAnswer null), 크루 밖 403")
    void attendance() throws Exception {
        Crew c = crew();
        long id = created(writeRun(c.l(), c.id(), run("참석", 3000, tomorrow(), null)));
        String path = "/api/v1/posts/" + id + "/attendance";
        call(c.r(), put(path), Map.of("answer", "GOING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myAnswer").value("GOING"))
                .andExpect(jsonPath("$.goingCount").value(2));
        call(c.r(), delete(path), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(id))
                .andExpect(jsonPath("$.myAnswer").doesNotExist())
                .andExpect(jsonPath("$.goingCount").value(1));
        call(c.r(), delete(path), null).andExpect(jsonPath("$.goingCount").value(1));
        call(c.r(), get("/api/v1/crew-runs/" + id), null)
                .andExpect(jsonPath("$.myAnswer").doesNotExist())
                .andExpect(jsonPath("$.attendees.length()").value(1));
        call(c.o(), delete(path), null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("지난 크루런: month 필수·형식 오류 400(INVALID_YEAR_MONTH), 빈 달은 빈 목록, 크루 밖 403")
    void past() throws Exception {
        Crew c = crew();
        String path = "/api/v1/crews/" + c.id() + "/crew-runs/past";
        call(c.r(), get(path + "?month=2026-10"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.dates").isEmpty())
                .andExpect(jsonPath("$.runs").isEmpty())
                .andExpect(jsonPath("$.earliestMonth").doesNotExist());
        call(c.r(), get(path + "?month=2026-10&date=2026-10-13"), null).andExpect(status().isOk());
        call(c.r(), get(path), null).andExpect(status().isBadRequest());
        for (String bad :
                List.of("?month=2026-13", "?month=202610", "?month=2026-10&date=2026-11-01")) {
            call(c.r(), get(path + bad), null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_YEAR_MONTH"));
        }
        call(c.o(), get(path + "?month=2026-10"), null).andExpect(status().isForbidden());
    }
}
