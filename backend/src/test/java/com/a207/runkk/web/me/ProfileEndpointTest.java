package com.a207.runkk.web.me;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.web.WebTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** GET /members/{id}/profile → ProfileQueryService.view(FR-005, FR-006, D-52). */
@DisplayName("web.me 회원 프로필 조회")
class ProfileEndpointTest extends WebTestSupport {

    @Test
    @DisplayName("본인은 SELF(설정 포함), 비공개 회원은 PRIVATE(태그·설정 없음), 공개 회원은 PUBLIC, 없는 회원은 400")
    void profileByDisclosure() throws Exception {
        Session me = signUp();
        Session other = signUp();
        mvc.perform(as(me, get("/api/v1/members/" + me.memberId() + "/profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disclosure").value("SELF"))
                .andExpect(jsonPath("$.settings.recordVisibility").value("PUBLIC"))
                .andExpect(jsonPath("$.tags[0]").value("SOLO_RUN"))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.ageBand").isNotEmpty());
        mvc.perform(as(me, get("/api/v1/members/" + other.memberId() + "/profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disclosure").value("PRIVATE"))
                .andExpect(jsonPath("$.displayName").isNotEmpty())
                .andExpect(jsonPath("$.settings").doesNotExist())
                .andExpect(jsonPath("$.level").doesNotExist())
                .andExpect(jsonPath("$.ageBand").doesNotExist())
                .andExpect(jsonPath("$.tags").isEmpty());
        mvc.perform(
                        as(other, patch("/api/v1/me/settings"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        write(
                                                Map.of(
                                                        "item",
                                                        "PROFILE_VISIBILITY",
                                                        "value",
                                                        "PUBLIC"))))
                .andExpect(status().isOk());
        mvc.perform(as(me, get("/api/v1/members/" + other.memberId() + "/profile")))
                .andExpect(jsonPath("$.disclosure").value("PUBLIC"))
                .andExpect(jsonPath("$.tags[0]").value("SOLO_RUN"))
                .andExpect(jsonPath("$.longestDistanceMeters").doesNotExist());
        mvc.perform(as(me, get("/api/v1/members/987654321/profile")))
                .andExpect(status().isBadRequest());
    }
}
