package com.a207.runkk.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("관리툴 로그인 시도 제한의 접속지 키")
class AdminClientKeyTest {

    private static MockHttpServletRequest from(String remote, String realIp) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        if (realIp != null) {
            request.addHeader("X-Real-IP", realIp);
        }
        return request;
    }

    @Test
    @DisplayName("X-Real-IP는 프록시(사설망·같은 호스트)에서 온 요청만 믿고, 밖에서 바로 오면 원격 주소를 쓴다")
    void trustsRealIpOnlyFromProxy() {
        assertThat(AdminAuthController.clientOf(from("172.18.0.5", "203.0.113.7")))
                .isEqualTo("203.0.113.7");
        assertThat(AdminAuthController.clientOf(from("127.0.0.1", "203.0.113.7")))
                .isEqualTo("203.0.113.7");
        assertThat(AdminAuthController.clientOf(from("8.8.8.8", "10.0.0.1"))).isEqualTo("8.8.8.8");
        assertThat(AdminAuthController.clientOf(from("172.18.0.5", null))).isEqualTo("172.18.0.5");
    }
}
