package com.a207.runkk.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.bootstrap.RunkkApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 1단계 리뷰 L11(Boot 기본 계정·httpBasic 제거)·L12(h2-console은 local 프로필에서만). */
@SpringBootTest(classes = RunkkApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("보안 설정")
class SecurityConfigTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    @DisplayName("L11: Boot 기본 사용자 저장소가 없고 Basic 인증으로 들어올 수 없다")
    void noDefaultUser_andBasicAuthIsIgnored() throws Exception {
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
        mvc.perform(
                        get("/api/v1/me/settings")
                                .header("Authorization", "Basic dXNlcjpwYXNzd29yZA=="))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("L12: local이 아닌 프로필에서는 h2-console이 공개되지 않는다")
    void h2Console_isNotPublicOutsideLocal() throws Exception {
        mvc.perform(get("/h2-console/")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("상태 확인 경로는 공개다")
    void health_isPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
