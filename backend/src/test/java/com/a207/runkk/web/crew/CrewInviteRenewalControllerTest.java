package com.a207.runkk.web.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 피그마 맞춤 W7(contract-W7.md 1장): 초대 코드 재발급 화면을 없애므로 크루원이 초대 코드를 읽을 때(GET /crews/{id}, GET /crews/me)
 * 7일이 지났으면 서버가 새 코드를 발급해 준다. 크루 밖 사람의 조회는 코드를 바꾸지 않는다.
 */
@DisplayName("web.crew W7 초대 코드 자동 재발급")
class CrewInviteRenewalControllerTest extends WebTestSupport {

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

    private long createCrew(Session leader) throws Exception {
        Map<String, Object> form = new HashMap<>();
        form.put("name", "재발급크루" + CREWS.incrementAndGet());
        form.put("tags", List.of("WEEKDAY_EVENING"));
        form.put("visibility", "PUBLIC");
        form.put("joinPolicy", "FREE");
        return json(call(leader, post("/api/v1/crews"), form).andExpect(status().isCreated()))
                .get("id")
                .asLong();
    }

    private void expire(long crewId) {
        jdbc.update(
                "update crew set invite_code_expires_at = ? where id = ?",
                LocalDateTime.now(clock).minusMinutes(1),
                crewId);
    }

    private String storedCode(long crewId) {
        return jdbc.queryForObject(
                "select invite_code from crew where id = ?", String.class, crewId);
    }

    @Test
    @DisplayName("만료된 코드는 크루원이 크루 상세를 열 때 새로 발급되고, 다시 열면 같은 새 코드다")
    void expiredCode_isRenewedOnMemberRead() throws Exception {
        Session leader = signUp();
        Session member = signUp();
        Session outsider = signUp();
        long crewId = createCrew(leader);
        call(member, post("/api/v1/crews/join"), Map.of("crewId", crewId))
                .andExpect(jsonPath("$.status").value("JOINED"));
        String old = storedCode(crewId);
        expire(crewId);

        JsonNode asOutsider = json(call(outsider, get("/api/v1/crews/" + crewId), null));
        assertThat(asOutsider.get("inviteCode").isNull()).isTrue();
        assertThat(storedCode(crewId)).as("크루 밖 조회는 코드를 바꾸지 않는다").isEqualTo(old);

        JsonNode asMember = json(call(member, get("/api/v1/crews/" + crewId), null));
        String renewed = asMember.get("inviteCode").asText();
        assertThat(renewed).matches("[A-Z0-9]{6}").isNotEqualTo(old);
        assertThat(storedCode(crewId)).isEqualTo(renewed);
        assertThat(OffsetDateTime.parse(asMember.get("inviteCodeExpiresAt").asText()))
                .isAfter(OffsetDateTime.now(clock).plusDays(6));

        assertThat(
                        json(call(leader, get("/api/v1/crews/me"), null))
                                .get("crew")
                                .get("inviteCode")
                                .asText())
                .as("유효한 코드는 그대로")
                .isEqualTo(renewed);
        call(outsider, post("/api/v1/crews/join"), Map.of("inviteCode", renewed))
                .andExpect(jsonPath("$.status").value("JOINED"));
    }

    @Test
    @DisplayName("내 크루(GET /crews/me)로 읽어도 만료된 코드를 새로 발급한다")
    void expiredCode_isRenewedOnMyCrew() throws Exception {
        Session leader = signUp();
        long crewId = createCrew(leader);
        String old = storedCode(crewId);
        expire(crewId);

        String renewed =
                json(call(leader, get("/api/v1/crews/me"), null))
                        .get("crew")
                        .get("inviteCode")
                        .asText();
        assertThat(renewed).isNotEqualTo(old).isEqualTo(storedCode(crewId));
    }
}
