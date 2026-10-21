package com.a207.runkk.web.admin;

import com.a207.runkk.common.AttemptLimiter;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ClientAddress;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.operator.OperatorAuthorizer;
import com.a207.runkk.operator.OperatorSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 로그인(설계 5.7.1 POST /admin/login → OperatorAuthorizer.authenticate, FR-173, R-05, D-50). 제공자는
 * 문자열로 넘겨 web.admin이 member 형식을 import하지 않는다(design-review.md R26). 화이트리스트 밖이면 403이다.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminAuthController {

    private static final int TOKEN_MAX = 4_096;
    private static final int PROVIDER_MAX = 10;

    /** 접속지별 실패 허용 횟수와 창(B5 리뷰: 로그인 시도 제한). 넘으면 429 TOO_MANY_ATTEMPTS. */
    static final int LOGIN_FAILURES = 10;

    static final Duration LOGIN_WINDOW = Duration.ofMinutes(10);

    /** nginx가 덮어써 넣는 실제 접속지 헤더(infra/nginx proxy_set_header X-Real-IP). */
    private static final String REAL_IP = "X-Real-IP";

    private final OperatorAuthorizer operatorAuthorizer;
    private final AttemptLimiter attempts;

    public AdminAuthController(OperatorAuthorizer operatorAuthorizer, Clock clock) {
        this.operatorAuthorizer = operatorAuthorizer;
        this.attempts = new AttemptLimiter(LOGIN_FAILURES, LOGIN_WINDOW, clock);
    }

    /** 같은 접속지에서 10분에 10번 실패하면 잠시 막는다(429). 성공하면 그 접속지의 실패를 지운다. */
    @PostMapping("/login")
    OperatorSession login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String client = clientOf(http);
        if (attempts.isBlocked(client)) {
            throw new BusinessRuleException(ReasonCode.TOO_MANY_ATTEMPTS);
        }
        try {
            OperatorSession session =
                    operatorAuthorizer.authenticate(request.provider(), request.providerToken());
            attempts.reset(client);
            return session;
        } catch (PermissionDeniedException | IllegalArgumentException e) {
            // 제공자 무응답(PROVIDER_UNAVAILABLE)은 운영자 잘못이 아니라 세지 않는다
            attempts.recordFailure(client);
            throw e;
        }
    }

    /** 접속지. X-Real-IP는 프록시(같은 호스트·사설망)에서 온 요청만 믿는다(common.ClientAddress). */
    static String clientOf(HttpServletRequest http) {
        return ClientAddress.resolve(http.getRemoteAddr(), http.getHeader(REAL_IP)).value();
    }

    /**
     * 운영자 로그인 요청.
     *
     * @param provider 제공자("KAKAO"·"GOOGLE")
     * @param providerToken 제공자 토큰
     */
    record LoginRequest(
            @NotBlank @Size(max = PROVIDER_MAX) String provider,
            @NotBlank @Size(max = TOKEN_MAX) String providerToken) {}
}
