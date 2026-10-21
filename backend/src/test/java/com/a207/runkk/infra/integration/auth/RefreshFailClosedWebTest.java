package com.a207.runkk.infra.integration.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.web.WebTestSupport;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

/**
 * M6 후속 결정(fail-closed): 폐기 목록을 쓸 수 없으면 POST /auth/refresh·/auth/logout은 503
 * PROVIDER_UNAVAILABLE이다. 토큰을 쓰지 않았으므로 저장소가 돌아오면 같은 갱신 토큰으로 다시 갱신할 수 있다.
 */
@DisplayName("갱신 토큰 폐기 목록 장애 때 갱신·로그아웃 거부(fail-closed)")
class RefreshFailClosedWebTest extends WebTestSupport {

    @MockitoSpyBean RefreshTokenRevocations revocations;

    @AfterEach
    void restore() {
        reset(revocations);
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(
                post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(write(Map.of("refreshToken", token))));
    }

    @Test
    @DisplayName("저장소 장애: 갱신 503 → 복구 뒤 같은 토큰으로 200, 로그아웃 503")
    void storeDown_refreshAndLogoutAre503_thenSameTokenWorks() throws Exception {
        Session s = signUp();
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(revocations)
                .claim(anyString(), any());
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(revocations)
                .revoke(anyString(), any());

        refresh(s.refreshToken())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"));
        mvc.perform(
                        as(s, post("/api/v1/auth/logout"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("refreshToken", s.refreshToken()))))
                .andExpect(status().isServiceUnavailable());

        reset(revocations);
        refresh(s.refreshToken()).andExpect(status().isOk());
        refresh(s.refreshToken()).andExpect(status().isUnauthorized());
    }
}
