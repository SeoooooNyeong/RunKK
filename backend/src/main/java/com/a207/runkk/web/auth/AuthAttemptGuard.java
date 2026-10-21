package com.a207.runkk.web.auth;

import com.a207.runkk.common.AttemptLimiter;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ClientAddress;
import com.a207.runkk.common.ReasonCode;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;

/**
 * 공개 인증 경로(POST /auth/login, /auth/refresh, /members) 시도 제한(코드 리뷰 2차 M5). 관리툴 로그인·초대 코드와 같은
 * AttemptLimiter 방식이다. 세 가지를 센다: 접속지별 요청 수(로그인 요청마다 카카오·구글 호출이 나가므로 폭주를 막는다), 접속지별 실패 수(무차별 대입),
 * 자격(제공자 토큰·갱신 토큰·가입 티켓)별 실패 수(접속지를 바꿔 가며 같은 자격을 시험하는 것). 넘으면 429 TOO_MANY_ATTEMPTS다. 자격은 SHA-256
 * 앞부분만 키로 남겨 토큰 원문을 메모리에 두지 않는다. 메모리 값이라 재시작하면 비워진다(5.4 인스턴스 1개).
 */
@Component
public class AuthAttemptGuard {

    /** nginx가 덮어써 넣는 실제 접속지 헤더(infra/nginx proxy_set_header X-Real-IP). */
    static final String REAL_IP = "X-Real-IP";

    private static final int KEY_BYTES = 16;

    private final AttemptLimiter clientRequests;
    private final AttemptLimiter clientFailures;
    private final AttemptLimiter credentialFailures;

    AuthAttemptGuard(Limits limits, Clock clock) {
        this.clientRequests =
                new AttemptLimiter(limits.clientRequests(), limits.requestWindow(), clock);
        this.clientFailures =
                new AttemptLimiter(limits.clientFailures(), limits.failureWindow(), clock);
        this.credentialFailures =
                new AttemptLimiter(limits.credentialFailures(), limits.failureWindow(), clock);
    }

    /** 요청 한 번을 받아들인다. 접속지나 자격이 막혀 있으면 TOO_MANY_ATTEMPTS. 받아들인 요청은 접속지 요청 수에 더한다. */
    Attempt admit(HttpServletRequest http, String credential) {
        Attempt attempt =
                new Attempt(
                        ClientAddress.resolve(http.getRemoteAddr(), http.getHeader(REAL_IP))
                                .value(),
                        credentialKey(credential));
        if (clientRequests.isBlocked(attempt.client())
                || clientFailures.isBlocked(attempt.client())
                || credentialFailures.isBlocked(attempt.credential())) {
            throw new BusinessRuleException(ReasonCode.TOO_MANY_ATTEMPTS);
        }
        clientRequests.recordFailure(attempt.client());
        return attempt;
    }

    /** 인증 실패(위조·만료 토큰, 제공자 거부) 한 번. 제공자 무응답처럼 요청자 잘못이 아닌 실패는 부르지 않는다. */
    void failed(Attempt attempt) {
        clientFailures.recordFailure(attempt.client());
        credentialFailures.recordFailure(attempt.credential());
    }

    private static String credentialKey(String credential) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(
                                    (credential == null ? "" : credential)
                                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, KEY_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256이 없다", e);
        }
    }

    /**
     * 받아들인 시도의 키.
     *
     * @param client 접속지
     * @param credential 자격 키(SHA-256 앞 16바이트)
     */
    record Attempt(String client, String credential) {}

    /**
     * 시도 제한 값(runkk.auth.attempts). 기본: 접속지마다 1분에 요청 30번, 10분에 실패 10번, 자격마다 10분에 실패 5번.
     *
     * @param clientRequests 접속지별 요청 상한(requestWindow 안)
     * @param requestWindow 요청 수 창
     * @param clientFailures 접속지별 실패 상한(failureWindow 안)
     * @param credentialFailures 자격별 실패 상한(failureWindow 안)
     * @param failureWindow 실패 수 창
     */
    @ConfigurationProperties("runkk.auth.attempts")
    record Limits(
            @DefaultValue("30") int clientRequests,
            @DefaultValue("1m") Duration requestWindow,
            @DefaultValue("10") int clientFailures,
            @DefaultValue("5") int credentialFailures,
            @DefaultValue("10m") Duration failureWindow) {}
}
