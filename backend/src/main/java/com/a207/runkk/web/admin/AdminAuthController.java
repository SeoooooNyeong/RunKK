package com.a207.runkk.web.admin;

import com.a207.runkk.operator.OperatorAuthorizer;
import com.a207.runkk.operator.OperatorSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    private final OperatorAuthorizer operatorAuthorizer;

    public AdminAuthController(OperatorAuthorizer operatorAuthorizer) {
        this.operatorAuthorizer = operatorAuthorizer;
    }

    @PostMapping("/login")
    OperatorSession login(@Valid @RequestBody LoginRequest request) {
        return operatorAuthorizer.authenticate(request.provider(), request.providerToken());
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
