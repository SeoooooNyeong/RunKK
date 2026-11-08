package com.a207.runkk.web.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * 운영자 공지 발송 POST /admin/notifications/notices(S15P31A207-275): 운영자만, 형식 400, 링크 규칙 409, 받은 회원의 알림함에
 * NOTICE가 제목과 링크 대상으로 보이고 열면 EXTERNAL_LINK와 주소를 준다.
 */
@DisplayName("web.admin.notification 운영자 공지")
class NoticeAdminControllerTest extends WebTestSupport {

    private static final String OPERATOR = "notice-op";
    private static final String PATH = "/api/v1/admin/notifications/notices";
    private static final String FORM = "https://forms.gle/AbCdEf123";

    @Autowired OperationSettingService settings;

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

    private static Map<String, Object> notice(List<Long> ids, String link) {
        Map<String, Object> body = new HashMap<>();
        body.put("memberIds", ids);
        body.put("title", "코치님 이벤트 당첨");
        body.put("body", "구글 폼에 연락처를 남겨 주세요");
        if (link != null) {
            body.put("linkUrl", link);
        }
        return body;
    }

    @Test
    @DisplayName("운영자 토큰이 없거나 회원 JWT면 403")
    void requiresOperator() throws Exception {
        Session member = signUp();
        String json = write(notice(List.of(member.memberId()), FORM));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        mvc.perform(as(member, post(PATH)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("보내면 결과를 주고 받은 회원 알림함에 제목, 본문, 링크가 보이며 열면 EXTERNAL_LINK와 주소")
    void send_showsInInbox() throws Exception {
        String bearer = operatorBearer();
        Session winner = signUp();
        mvc.perform(
                        post(PATH)
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                notice(
                                                        List.of(winner.memberId(), 99_999_999L),
                                                        FORM))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(2))
                .andExpect(jsonPath("$.stored").value(1))
                .andExpect(jsonPath("$.skippedMemberIds[0]").value(99_999_999L));

        String inbox =
                mvc.perform(as(winner, get("/api/v1/notifications")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items[0].type").value("NOTICE"))
                        .andExpect(jsonPath("$.items[0].title").value("코치님 이벤트 당첨"))
                        .andExpect(jsonPath("$.items[0].content").value("구글 폼에 연락처를 남겨 주세요"))
                        .andExpect(jsonPath("$.items[0].target.screen").value("EXTERNAL_LINK"))
                        .andExpect(jsonPath("$.items[0].target.targetId").value(FORM))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long id = body(inbox).get("items").get(0).get("id").asLong();
        mvc.perform(as(winner, post("/api/v1/notifications/" + id + "/open")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("EXTERNAL_LINK"))
                .andExpect(jsonPath("$.targetId").value(FORM));
    }

    @Test
    @DisplayName("형식이 틀리면 400(빈 명단, 101명, 빈 제목), 구글 폼이 아닌 링크는 409 VALUE_INVALID")
    void send_rejectsInvalid() throws Exception {
        String bearer = operatorBearer();
        Session winner = signUp();
        Map<String, Object> blankTitle = notice(List.of(winner.memberId()), FORM);
        blankTitle.put("title", " ");
        for (Map<String, Object> bad :
                List.of(
                        notice(List.of(), FORM),
                        notice(LongStream.rangeClosed(1, 101).boxed().toList(), FORM),
                        blankTitle)) {
            mvc.perform(
                            post(PATH)
                                    .header("Authorization", bearer)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(write(bad)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(
                        post(PATH)
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                notice(
                                                        List.of(winner.memberId()),
                                                        "http://forms.gle/AbCdEf123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VALUE_INVALID"));
        mvc.perform(as(winner, get("/api/v1/notifications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }
}
