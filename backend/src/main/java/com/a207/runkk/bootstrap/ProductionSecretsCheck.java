package com.a207.runkk.bootstrap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * prod 프로필에서 필수 비밀값이 비면 기동을 멈춘다(단계별 자가 리뷰 B4·B5). local·test·develop은 지금처럼 비어도 뜬다(JWT 키는 기동마다 임의 키,
 * ai 내부 토큰은 빈 값). prod에서 비면 재시작마다 모든 토큰이 무효가 되거나(JWT_SECRET·ADMIN_JWT_SECRET) ai 서버가 모든 요청을 401로
 * 거절해(RUNKK_INTERNAL_TOKEN) 조용히 기능이 꺼진다. 값은 로그에 남기지 않고 빈 환경 변수 이름만 알린다.
 */
@Component
public class ProductionSecretsCheck {

    /** HS256 서명 키 최소 길이(JwtTokenIssuer·JwtOperatorTokenIssuer와 같다). */
    static final int MIN_SECRET_BYTES = 32;

    static final String JWT_SECRET = "runkk.auth.jwt-secret";
    static final String ADMIN_JWT_SECRET = "runkk.auth.admin-jwt-secret";
    static final String INTERNAL_TOKEN = "runkk.ai.internal-token";

    public ProductionSecretsCheck(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            List<String> problems = problemsOf(environment);
            if (!problems.isEmpty()) {
                throw new IllegalStateException(
                        "prod 필수 비밀값 오류(.env를 확인): " + String.join(", ", problems));
            }
        }
    }

    static List<String> problemsOf(Environment environment) {
        String member = environment.getProperty(JWT_SECRET, "");
        String admin = environment.getProperty(ADMIN_JWT_SECRET, "");
        String internal = environment.getProperty(INTERNAL_TOKEN, "");
        List<String> problems = new ArrayList<>();
        requireKey(problems, "JWT_SECRET", member);
        requireKey(problems, "ADMIN_JWT_SECRET", admin);
        if (!member.isBlank() && member.equals(admin)) {
            problems.add("ADMIN_JWT_SECRET이 JWT_SECRET과 같음");
        }
        if (internal.isBlank()) {
            problems.add("RUNKK_INTERNAL_TOKEN 비어 있음");
        }
        return problems;
    }

    private static void requireKey(List<String> problems, String name, String value) {
        if (value.isBlank()) {
            problems.add(name + " 비어 있음");
        } else if (value.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            problems.add(name + " 32바이트 미만");
        }
    }
}
