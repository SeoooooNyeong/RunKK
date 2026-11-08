package com.a207.runkk.web.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.crew.chat.ChatDraft;
import com.a207.runkk.crew.chat.CrewChatService;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
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
 * 5.7.1 채팅 엔드포인트 5개(F.2.2 크루 채팅). 인증 필터를 거치고 test 프로필(H2 + 내장 Redis)에서 돈다. T-27
 * TC-6691~TC-6700(전송·알림 부분은 ChatSocketIntegrationTest), TC-6876.
 */
@DisplayName("web.chat 채팅 엔드포인트")
class ChatControllerTest extends WebTestSupport {

    private static final AtomicInteger CREWS = new AtomicInteger();

    @Autowired CrewChatService chatService;
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

    /** 크루장 leader와 자유 가입한 members로 이뤄진 크루. */
    private long crewOf(Session leader, Session... members) throws Exception {
        Map<String, Object> form = new HashMap<>();
        form.put("name", "채팅크루" + CREWS.incrementAndGet());
        form.put("intro", "함께 달려요");
        form.put("tags", List.of("WEEKDAY_EVENING"));
        form.put("visibility", "PUBLIC");
        form.put("joinPolicy", "FREE");
        long crewId =
                json(call(leader, post("/api/v1/crews"), form).andExpect(status().isCreated()))
                        .get("id")
                        .asLong();
        for (Session m : members) {
            call(m, post("/api/v1/crews/join"), Map.of("crewId", crewId))
                    .andExpect(jsonPath("$.status").value("JOINED"));
        }
        return crewId;
    }

    private static Map<String, Object> message(String content) {
        return Map.of("clientMessageId", UUID.randomUUID().toString(), "content", content);
    }

    private String messages(long crewId) {
        return "/api/v1/crews/" + crewId + "/chat/messages";
    }

    @Test
    @DisplayName(
            "TC-6691·TC-6692·TC-6693 보내기: 201 ChatMessageItem, 같은 clientMessageId 재전송 200 같은 메시지, 빈"
                    + " 값·501자·크루 밖 409")
    void postMessage() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        Session outsider = signUp();
        long crewId = crewOf(leader, a);

        Map<String, Object> first = message("내일 7시 출발");
        JsonNode created =
                json(call(a, post(messages(crewId)), first).andExpect(status().isCreated()));
        assertThat(created.get("authorId").asLong()).isEqualTo(a.memberId());
        assertThat(created.get("content").asText()).isEqualTo("내일 7시 출발");
        assertThat(created.get("mine").asBoolean()).isTrue();
        assertThat(created.get("canDelete").asBoolean()).isTrue();
        assertThat(created.get("clientMessageId").asText()).isEqualTo(first.get("clientMessageId"));
        assertThat(created.get("sentAt").asText()).endsWith("+09:00");
        assertThat(created.get("reactions").isEmpty()).isTrue();

        JsonNode again = json(call(a, post(messages(crewId)), first).andExpect(status().isOk()));
        assertThat(again.get("messageId").asLong()).isEqualTo(created.get("messageId").asLong());
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_chat_message where crew_id = ?",
                                Integer.class,
                                crewId))
                .isEqualTo(1);

        // 회원당 초당 5건 제한(ChatRateGuard)에 걸리지 않게 거부 경계는 크루장이 보낸다
        call(leader, post(messages(crewId)), message(""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_EMPTY"));
        call(leader, post(messages(crewId)), message("   "))
                .andExpect(jsonPath("$.code").value("CHAT_EMPTY"));
        String thumbs = "👍";
        call(leader, post(messages(crewId)), message(thumbs.repeat(100) + "a".repeat(400)))
                .andExpect(status().isCreated());
        call(leader, post(messages(crewId)), message("a".repeat(501)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BODY_TOO_LONG"));
        call(outsider, post(messages(crewId)), message("안녕"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CREW_MEMBER"));
        call(a, post(messages(crewId)), Map.of("clientMessageId", "short", "content", "안녕"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
            "TC-6696·TC-6876 조회: 최근 50 오래된 → 새 순·hasMore, size 0·100·101, before·after 함께 400, 가입 전"
                    + " 메시지 보임, 떠난 회원 409")
    void readHistory() throws Exception {
        Session leader = signUp();
        Session late = signUp();
        long crewId = crewOf(leader);
        for (int i = 1; i <= 120; i++) {
            chatService.post(
                    leader.memberId(),
                    crewId,
                    new ChatDraft(UUID.randomUUID().toString(), "메시지 " + i));
        }
        call(late, post("/api/v1/crews/join"), Map.of("crewId", crewId)).andExpect(status().isOk());

        JsonNode latest = json(call(late, get(messages(crewId)), null).andExpect(status().isOk()));
        assertThat(latest.get("messages")).hasSize(50);
        assertThat(latest.get("hasMore").asBoolean()).isTrue();
        assertThat(latest.get("messages").get(0).get("content").asText()).isEqualTo("메시지 71");
        assertThat(latest.get("messages").get(49).get("content").asText()).isEqualTo("메시지 120");
        assertThat(latest.get("messages").get(0).get("mine").asBoolean()).isFalse();
        assertThat(latest.get("messages").get(0).get("clientMessageId").isNull()).isTrue();

        long oldest = latest.get("messages").get(0).get("messageId").asLong();
        JsonNode older =
                json(call(late, get(messages(crewId) + "?before=" + oldest + "&size=100"), null));
        assertThat(older.get("messages")).hasSize(70);
        assertThat(older.get("hasMore").asBoolean()).isFalse();
        assertThat(older.get("messages").get(0).get("content").asText()).isEqualTo("메시지 1");

        assertThat(json(call(late, get(messages(crewId) + "?size=0"), null)).get("messages"))
                .hasSize(50);
        assertThat(json(call(late, get(messages(crewId) + "?size=101"), null)).get("messages"))
                .hasSize(100);
        long newest = latest.get("messages").get(49).get("messageId").asLong();
        JsonNode after = json(call(late, get(messages(crewId) + "?after=" + (newest - 3)), null));
        assertThat(after.get("messages")).hasSize(3);
        assertThat(after.get("hasMore").asBoolean()).isFalse();

        call(late, get(messages(crewId) + "?before=10&after=5"), null)
                .andExpect(status().isBadRequest());

        call(late, post("/api/v1/crews/" + crewId + "/leave"), null)
                .andExpect(status().isNoContent());
        call(late, get(messages(crewId)), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CREW_MEMBER"));
    }

    @Test
    @DisplayName("토큰이 없으면 다섯 엔드포인트 모두 401")
    void unauthenticated() throws Exception {
        mvc.perform(get("/api/v1/crews/1/chat/messages")).andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/crews/1/chat/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(message("안녕"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/chat-messages/1")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/chat-messages/1/reactions/LIKE"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/chat-messages/1/reactions/HEART"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-6694 회원당 초당 5건을 넘으면 429 TOO_MANY_ATTEMPTS(저장 0)")
    void rateLimit() throws Exception {
        Session leader = signUp();
        long crewId = crewOf(leader);
        int created = 0;
        boolean limited = false;
        for (int i = 0; i < 50 && !limited; i++) {
            int status =
                    call(leader, post(messages(crewId)), message("빠르게 " + i))
                            .andReturn()
                            .getResponse()
                            .getStatus();
            if (status == 429) {
                limited = true;
            } else {
                created++;
            }
        }
        assertThat(limited).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_chat_message where crew_id = ?",
                                Integer.class,
                                crewId))
                .isEqualTo(created);
    }

    @Test
    @DisplayName(
            "TC-6700 삭제: 본인 204, 크루원이 남의 것 403, 운영진이 남의 것 204 + 감사 CHAT_FORCE_DELETE(CREW_MANAGER),"
                    + " 이미 삭제 409")
    void deleteMessage() throws Exception {
        Session leader = signUp();
        Session a = signUp();
        Session b = signUp();
        Session manager = signUp();
        long crewId = crewOf(leader, a, b, manager);
        jdbc.update(
                "update crew_membership set role = 'MANAGER' where crew_id = ? and member_id = ?",
                crewId,
                manager.memberId());
        long own = json(call(a, post(messages(crewId)), message("내 글"))).get("messageId").asLong();
        long target =
                json(call(a, post(messages(crewId)), message("지울 글"))).get("messageId").asLong();

        call(a, delete("/api/v1/chat-messages/" + own), null).andExpect(status().isNoContent());
        call(b, delete("/api/v1/chat-messages/" + target), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        JsonNode managerView = json(call(manager, get(messages(crewId)), null));
        assertThat(managerView.get("messages").get(0).get("canDelete").asBoolean()).isTrue();
        call(manager, delete("/api/v1/chat-messages/" + target), null)
                .andExpect(status().isNoContent());
        assertThat(
                        jdbc.queryForObject(
                                "select actor_role from audit_record where action ="
                                        + " 'CHAT_FORCE_DELETE' and target_id = ?",
                                String.class,
                                String.valueOf(target)))
                .isEqualTo("CREW_MANAGER");
        call(a, delete("/api/v1/chat-messages/" + own), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_MESSAGE_NOT_FOUND"));
        assertThat(json(call(a, get(messages(crewId)), null)).get("messages")).isEmpty();
    }

    @Test
    @DisplayName("TC-6699 리액션: 두 번 달아도 1, 다른 종류·다른 회원, 떼기(없으면 그대로), 모르는 종류 400")
    void reactions() throws Exception {
        Session leader = signUp();
        Session b = signUp();
        Session c = signUp();
        long crewId = crewOf(leader, b, c);
        long id =
                json(call(leader, post(messages(crewId)), message("좋아요 눌러줘")))
                        .get("messageId")
                        .asLong();
        String like = "/api/v1/chat-messages/" + id + "/reactions/LIKE";
        String heart = "/api/v1/chat-messages/" + id + "/reactions/HEART";

        call(b, put(like), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions.LIKE").value(1));
        call(b, put(like), null).andExpect(jsonPath("$.reactions.LIKE").value(1));
        call(b, put(heart), null).andExpect(jsonPath("$.reactions.HEART").value(1));
        JsonNode both = json(call(c, put(like), null));
        assertThat(both.get("reactions").get("LIKE").asInt()).isEqualTo(2);
        assertThat(both.get("myReactions")).hasSize(1);
        JsonNode removed = json(call(b, delete(heart), null).andExpect(status().isOk()));
        assertThat(removed.get("reactions").has("HEART")).isFalse();
        assertThat(removed.get("myReactions").get(0).asText()).isEqualTo("LIKE");
        call(c, delete(heart), null).andExpect(jsonPath("$.reactions.LIKE").value(2));
        call(b, put("/api/v1/chat-messages/" + id + "/reactions/WOW"), null)
                .andExpect(status().isBadRequest());
        call(b, put("/api/v1/chat-messages/999999/reactions/LIKE"), null)
                .andExpect(jsonPath("$.code").value("CHAT_MESSAGE_NOT_FOUND"));

        JsonNode line = json(call(b, get(messages(crewId)), null)).get("messages").get(0);
        assertThat(line.get("reactions").get("LIKE").asInt()).isEqualTo(2);
        assertThat(line.get("myReactions").get(0).asText()).isEqualTo("LIKE");
    }

    @Test
    @DisplayName("BE5-B-18 리액션을 달았다 뗐다 몰아 하면 회원당 초당 10건을 넘는 순간 429 TOO_MANY_ATTEMPTS")
    void reactionRateLimit() throws Exception {
        Session leader = signUp();
        Session b = signUp();
        long crewId = crewOf(leader, b);
        long id =
                json(call(leader, post(messages(crewId)), message("누르기 폭주")))
                        .get("messageId")
                        .asLong();
        String like = "/api/v1/chat-messages/" + id + "/reactions/LIKE";
        boolean limited = false;
        for (int i = 0; i < 40 && !limited; i++) {
            limited =
                    call(b, i % 2 == 0 ? put(like) : delete(like), null)
                                    .andReturn()
                                    .getResponse()
                                    .getStatus()
                            == 429;
        }
        assertThat(limited).isTrue();
    }
}
