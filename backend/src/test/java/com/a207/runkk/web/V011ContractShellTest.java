package com.a207.runkk.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * v0.11 S0 계약 고정(부록 F F.11.1): 새 엔드포인트 24개는 경로·인증·요청 검사까지 살아 있고, 바뀐 엔드포인트는 기존 동작을 유지하면서 새 응답 항목을
 * 싣는다. 501 껍데기는 구현 단계가 모두 연결했다(크루 S3a, 채팅 S3b, 신고 S3c: ReportControllerTest).
 */
@DisplayName("v0.11 S0 계약 껍데기")
class V011ContractShellTest extends WebTestSupport {

    private static final String API = "/api/v1";

    private ResultActions perform(
            String method, String path, Object body, MockHttpServletRequestBuilder base)
            throws Exception {
        MockHttpServletRequestBuilder r = base;
        if (body != null) {
            r = r.contentType(MediaType.APPLICATION_JSON).content(write(body));
        }
        return mvc.perform(r);
    }

    @Test
    @DisplayName("요청 검사는 501보다 먼저다(필수 항목 빠짐 400)")
    void validation_runsBeforeShell() throws Exception {
        Session s = signUp();
        perform("PUT", "/posts/1/attendance", Map.of(), as(s, put(API + "/posts/1/attendance")))
                .andExpect(status().isBadRequest());
        perform("POST", "/members/guest", Map.of(), post(API + "/members/guest"))
                .andExpect(status().isBadRequest());
        perform(
                        "POST",
                        "/crews/1/chat/messages",
                        Map.of("clientMessageId", "짧음", "content", "안녕"),
                        as(s, post(API + "/crews/1/chat/messages")))
                .andExpect(status().isBadRequest());
        perform(
                        "PUT",
                        "/chat-messages/1/reactions/WOW",
                        null,
                        as(s, put(API + "/chat-messages/1/reactions/WOW")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /crews/{id}/posts: BOARD는 400(board-posts로 쓴다)")
    void postWrite_boardIsRejected() throws Exception {
        Session s = signUp();
        perform(
                        "POST",
                        "/crews/1/posts",
                        Map.of("kind", "BOARD", "body", "본문"),
                        as(s, post(API + "/crews/1/posts")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("바뀐 응답은 새 항목을 싣는다(값은 해당 단계 전까지 기본값)")
    void changedResponses_carryNewFields() throws Exception {
        Session s = signUp();
        mvc.perform(as(s, get(API + "/members/" + s.memberId() + "/profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarBackgroundKey").hasJsonPath());
        mvc.perform(as(s, get(API + "/avatar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backgroundKey").hasJsonPath());

        Map<String, Object> form = new HashMap<>();
        form.put("name", "계약" + (System.nanoTime() % 100000));
        form.put("intro", "소개");
        form.put("tags", List.of("WEEKDAY_EVENING"));
        form.put("visibility", "PUBLIC");
        form.put("joinPolicy", "FREE");
        long crewId =
                body(mvc.perform(
                                        as(s, post(API + "/crews"))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(write(form)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("id")
                        .asLong();
        mvc.perform(as(s, get(API + "/crews/" + crewId + "/race")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalMeters").hasJsonPath())
                .andExpect(jsonPath("$.goalProgressPercent").hasJsonPath())
                .andExpect(jsonPath("$.achievedAt").hasJsonPath())
                .andExpect(jsonPath("$.celebration").hasJsonPath());
        perform(
                        "POST",
                        "/crews/" + crewId + "/posts",
                        Map.of("kind", "CHEER", "body", "화이팅"),
                        as(s, post(API + "/crews/" + crewId + "/posts")))
                .andExpect(status().isCreated());
        mvc.perform(as(s, get(API + "/crews/" + crewId + "/posts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].canDelete").value(true))
                .andExpect(jsonPath("$.posts[0].meetup").hasJsonPath())
                .andExpect(jsonPath("$.posts[0].notice").hasJsonPath())
                .andExpect(jsonPath("$.posts[0].poll").hasJsonPath())
                .andExpect(jsonPath("$.posts[0].photos").hasJsonPath());
        Map<String, Object> run = new HashMap<>();
        run.put("kind", "CREW_RUN");
        run.put("body", "같이 뛰어요");
        run.put("distanceMeters", 3000);
        run.put(
                "scheduledAt",
                java.time.OffsetDateTime.now()
                        .plusDays(2)
                        .truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                        .toString()); // 5분 단위(W6)
        perform(
                        "POST",
                        "/crews/" + crewId + "/posts",
                        run,
                        as(s, post(API + "/crews/" + crewId + "/posts")))
                .andExpect(status().isCreated());
        // 새 종류로 거르면 그 종류의 글만 돌려준다(크루런 글을 잘못 돌려주지 않는다)
        for (String kind : List.of("MEETUP", "NOTICE", "POLL", "BOARD")) {
            mvc.perform(as(s, get(API + "/crews/" + crewId + "/posts").param("kind", kind)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.posts").isEmpty());
        }
        mvc.perform(as(s, get(API + "/crews/" + crewId + "/posts").param("kind", "CHEER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].kind").value("CHEER"));
    }
}
