package com.a207.runkk.infra.integration.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 서명 키(runkk.auth.*, D-19, D-42, 코드 리뷰 2차 L1). 비면 기동마다 임의 키를 만들고 prod는 비면 기동을
 * 멈춘다(ProductionSecretsCheck). 키 값은 로그에 남기지 않는다.
 *
 * @param jwtSecret runkk.auth.jwt-secret(회원 토큰)
 * @param adminJwtSecret runkk.auth.admin-jwt-secret(관리툴 토큰, 회원 키와 달라야 한다)
 */
@ConfigurationProperties("runkk.auth")
public record AuthSecrets(String jwtSecret, String adminJwtSecret) {

    @Override
    public String toString() {
        return "AuthSecrets[***]";
    }
}
