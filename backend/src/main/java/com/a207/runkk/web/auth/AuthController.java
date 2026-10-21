package com.a207.runkk.web.auth;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.AuthService;
import com.a207.runkk.member.LoginResult;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.ProviderUnavailableException;
import com.a207.runkk.member.TokenPair;
import jakarta.servlet.http.HttpServletRequest;
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
 * 서비스 입력으로, 결과를 응답 DTO로 바꾸기만 한다. 로그인 실패는 401(인증 실패)·503(제공자 무응답)과 공통 오류 응답(code·message)이다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final int TOKEN_MAX = 4096;

    private final AuthService authService;
    private final AuthAttemptGuard attempts;

    public AuthController(AuthService authService, AuthAttemptGuard attempts) {
        this.authService = authService;
        this.attempts = attempts;
    }

    /**
     * 로그인 실패는 BusinessRuleException(사유 코드)으로 던져 공통 오류 응답(code·message)이 된다: 401 AUTH_FAILED, 503
     * PROVIDER_UNAVAILABLE(ApiExceptionHandler가 사유 코드로 상태를 고른다). 접속지·제공자 토큰별 시도 제한을 넘으면 429(M5).
     */
    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthAttemptGuard.Attempt attempt =
                attempts.admit(http, request.provider() + ":" + request.providerToken());
        LoginResult result = authService.login(request.provider(), request.providerToken());
        if (result.isLoggedIn()) {
            return ResponseEntity.ok(LoginResponse.loggedIn(result.tokens()));
        }
        if (result.isSignUpRequired()) {
            return ResponseEntity.ok(LoginResponse.signUpRequired(result.signUpTicket()));
        }
        if (result.failure() == ReasonCode.AUTH_FAILED) {
            attempts.failed(attempt); // 제공자 무응답은 요청자 잘못이 아니라 세지 않는다
        }
        throw new BusinessRuleException(result.failure());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @RequestAttribute(AuthFilter.MEMBER_ID) long memberId,
            @Valid @RequestBody RefreshRequest request) {
        try {
            authService.logout(memberId, request.refreshToken());
        } catch (ProviderUnavailableException e) {
            throw new BusinessRuleException(ReasonCode.PROVIDER_UNAVAILABLE);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 갱신 실패는 401(본문 없음). 접속지·갱신 토큰별 시도 제한을 넘으면 429(M5). 폐기 목록을 확인할 수 없으면 503 PROVIDER_UNAVAILABLE이고
     * 갱신 토큰을 쓰지 않았으므로 앱은 같은 토큰으로 다시 시도할 수 있다(M6 후속 결정, fail-closed). 시도 제한 실패로 세지 않는다.
     */
    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        AuthAttemptGuard.Attempt attempt = attempts.admit(http, request.refreshToken());
        try {
            return ResponseEntity.ok(TokenResponse.of(authService.refresh(request.refreshToken())));
        } catch (AuthFailureException e) {
            attempts.failed(attempt);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (ProviderUnavailableException e) {
            throw new BusinessRuleException(ReasonCode.PROVIDER_UNAVAILABLE);
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
     * 로그인 결과(성공만). status는 LOGGED_IN·SIGN_UP_REQUIRED. 실패는 공통 오류 응답이다.
     *
     * @param status 결과
     * @param accessToken 접근 토큰(로그인완료)
     * @param refreshToken 갱신 토큰(로그인완료)
     * @param signUpTicket 가입 티켓(가입필요, 15분)
     */
    record LoginResponse(
            String status, String accessToken, String refreshToken, String signUpTicket) {

        static LoginResponse loggedIn(TokenPair tokens) {
            return new LoginResponse(
                    "LOGGED_IN", tokens.accessToken(), tokens.refreshToken(), null);
        }

        static LoginResponse signUpRequired(String ticket) {
            return new LoginResponse("SIGN_UP_REQUIRED", null, null, ticket);
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
