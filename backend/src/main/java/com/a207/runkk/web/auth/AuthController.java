package com.a207.runkk.web.auth;

import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.AuthService;
import com.a207.runkk.member.LoginResult;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.TokenPair;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인·로그아웃·토큰 갱신(설계 5.7.1: POST /auth/login, /auth/logout, /auth/refresh → AuthService). 요청 DTO를
 * 서비스 입력으로, 결과를 응답 DTO로 바꾸기만 한다. 로그인 실패는 401(인증 실패)·503(제공자 무응답)으로 사유 코드와 함께 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final int TOKEN_MAX = 4096;

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request.provider(), request.providerToken());
        if (result.isLoggedIn()) {
            return ResponseEntity.ok(LoginResponse.loggedIn(result.tokens()));
        }
        if (result.isSignUpRequired()) {
            return ResponseEntity.ok(LoginResponse.signUpRequired(result.signUpTicket()));
        }
        HttpStatus status =
                result.failure() == ReasonCode.PROVIDER_UNAVAILABLE
                        ? HttpStatus.SERVICE_UNAVAILABLE
                        : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(LoginResponse.failed(result.failure()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @RequestAttribute(AuthFilter.MEMBER_ID) long memberId,
            @Valid @RequestBody RefreshRequest request) {
        authService.logout(memberId, request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        try {
            return ResponseEntity.ok(TokenResponse.of(authService.refresh(request.refreshToken())));
        } catch (AuthFailureException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * 로그인 요청.
     *
     * @param provider KAKAO 또는 GOOGLE
     * @param providerToken 제공자 SDK가 준 토큰(카카오 접근 토큰, 구글 ID 토큰)
     */
    record LoginRequest(
            @NotNull ProviderType provider,
            @NotBlank @Size(max = TOKEN_MAX) String providerToken) {}

    /**
     * 토큰 갱신·로그아웃 요청.
     *
     * @param refreshToken 갱신 토큰
     */
    record RefreshRequest(@NotBlank @Size(max = TOKEN_MAX) String refreshToken) {}

    /**
     * 로그인 결과. status는 LOGGED_IN·SIGN_UP_REQUIRED·FAILED.
     *
     * @param status 결과
     * @param accessToken 접근 토큰(로그인완료)
     * @param refreshToken 갱신 토큰(로그인완료)
     * @param signUpTicket 가입 티켓(가입필요, 15분)
     * @param reason 실패 사유 코드
     * @param message 실패 화면 문구
     */
    record LoginResponse(
            String status,
            String accessToken,
            String refreshToken,
            String signUpTicket,
            String reason,
            String message) {

        static LoginResponse loggedIn(TokenPair tokens) {
            return new LoginResponse(
                    "LOGGED_IN", tokens.accessToken(), tokens.refreshToken(), null, null, null);
        }

        static LoginResponse signUpRequired(String ticket) {
            return new LoginResponse("SIGN_UP_REQUIRED", null, null, ticket, null, null);
        }

        static LoginResponse failed(ReasonCode reason) {
            return new LoginResponse("FAILED", null, null, null, reason.name(), reason.message());
        }
    }

    /**
     * 토큰 쌍.
     *
     * @param accessToken 접근 토큰(1시간)
     * @param refreshToken 갱신 토큰(14일, 한 번만 쓴다)
     */
    record TokenResponse(String accessToken, String refreshToken) {

        static TokenResponse of(TokenPair pair) {
            return new TokenResponse(pair.accessToken(), pair.refreshToken());
        }
    }
}
