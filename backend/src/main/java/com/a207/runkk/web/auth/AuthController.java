package com.a207.runkk.web.auth;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.AccountType;
import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.AuthService;
import com.a207.runkk.member.LoginResult;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.ProviderUnavailableException;
import com.a207.runkk.member.SocialLinkResult;
import com.a207.runkk.member.TokenPair;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인·로그아웃·토큰 갱신(설계 5.7.1: POST /auth/login, /auth/logout, /auth/refresh → AuthService), v0.11 게스트
 * 세션 재개(POST /auth/guest/session)와 소셜 계정 연동(POST /me/social-account), v0.21 워치용 토큰 쌍(POST
 * /auth/watch-session)과 워치연동 끄기(DELETE /auth/watch-session, W25). 요청 DTO를 서비스 입력으로, 결과를 응답 DTO로
 * 바꾸기만 한다. 로그인 실패는 401(인증 실패)·503(제공자 무응답)과 공통 오류 응답(code·message)이다.
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private static final int TOKEN_MAX = 4096;

    /** 게스트 자격 증명 길이 상한(부록 F F.2.2, 1~200자). */
    private static final int GUEST_CREDENTIAL_MAX = 200;

    private final AuthService authService;
    private final AuthAttemptGuard attempts;
    private final Clock clock;

    public AuthController(AuthService authService, AuthAttemptGuard attempts, Clock clock) {
        this.authService = authService;
        this.attempts = attempts;
        this.clock = clock;
    }

    /**
     * 로그인 실패는 BusinessRuleException(사유 코드)으로 던져 공통 오류 응답(code·message)이 된다: 401 AUTH_FAILED, 503
     * PROVIDER_UNAVAILABLE(ApiExceptionHandler가 사유 코드로 상태를 고른다). 접속지·제공자 토큰별 시도 제한을 넘으면 429(M5).
     */
    @PostMapping("/auth/login")
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

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(
            @RequestAttribute(AuthFilter.MEMBER_ID) long memberId,
            @Valid @RequestBody RefreshRequest request) {
        try {
            authService.logout(memberId, request.refreshToken());
        } catch (ProviderUnavailableException e) {
            // 로그아웃은 제공자를 부르지 않는다: 폐기 목록 저장소 장애다(B-L6)
            throw new BusinessRuleException(ReasonCode.TEMPORARILY_UNAVAILABLE);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 갱신 실패는 401(본문 없음). 접속지·갱신 토큰별 시도 제한을 넘으면 429(M5). 폐기 목록을 확인할 수 없으면 503
     * TEMPORARILY_UNAVAILABLE이고 갱신 토큰을 쓰지 않았으므로 앱은 같은 토큰으로 다시 시도할 수 있다(M6 후속 결정, fail-closed). 시도
     * 제한 실패로 세지 않는다.
     */
    @PostMapping("/auth/refresh")
    ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        AuthAttemptGuard.Attempt attempt = attempts.admit(http, request.refreshToken());
        try {
            return ResponseEntity.ok(TokenResponse.of(authService.refresh(request.refreshToken())));
        } catch (AuthFailureException e) {
            attempts.failed(attempt);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (ProviderUnavailableException e) {
            // 갱신은 제공자를 부르지 않는다: 원인은 서버 저장소라 "로그인 제공자 연결 실패"가 아니다(B-L6)
            throw new BusinessRuleException(ReasonCode.TEMPORARILY_UNAVAILABLE);
        }
    }

    /**
     * 워치용 토큰 쌍(v0.21 FR-240, D-372, 인증 필요, 본문 없음). 폰의 토큰 쌍은 그대로 유효하다. 탈퇴 회원은 인증 필터가 401로 막고, 그 사이에
     * 탈퇴했으면 갱신 실패와 같은 401(본문 없음)이다. 갱신 토큰을 새로 만드는 경로라 접속지별 요청 상한과 회원별 발급 한도를 센다(넘으면 429). 워치용 접근
     * 토큰으로 오면 403 PERMISSION_DENIED다(W26, 폰만 발급 요청).
     */
    @PostMapping("/auth/watch-session")
    ResponseEntity<TokenResponse> watchSession(
            @RequestAttribute(AuthFilter.MEMBER_ID) long memberId,
            @RequestAttribute(AuthFilter.WATCH_CLIENT) boolean watchClient,
            HttpServletRequest http) {
        if (watchClient) {
            // W26: 워치 세션 발급은 폰만 요청한다. 워치 토큰으로 새 워치 갱신 토큰을 만들어 일괄 폐기를 피하지 못하게 한다
            throw new PermissionDeniedException("watch_token_cannot_issue_watch_session");
        }
        attempts.admitWatchSession(http, memberId);
        try {
            return ResponseEntity.ok(TokenResponse.of(authService.issueWatchSession(memberId)));
        } catch (AuthFailureException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (ProviderUnavailableException e) {
            // 워치 세션 세대 저장소 장애다(제공자를 부르지 않는다)
            throw new BusinessRuleException(ReasonCode.TEMPORARILY_UNAVAILABLE);
        }
    }

    /**
     * 워치연동 끄기(2026-11-01 사용자 결정 W25, contract-v2 4절, 인증 필요, 본문 없음). 그 회원의 워치용 갱신 토큰을 모두 폐기하고 204다.
     * 폰 토큰은 그대로다. 워치의 접근 토큰은 만료(최대 1시간)까지 남는다. 여러 번 불러도 같다. 저장소 장애는 503 TEMPORARILY_UNAVAILABLE(다시
     * 불러도 된다).
     */
    @DeleteMapping("/auth/watch-session")
    ResponseEntity<Void> revokeWatchSessions(
            @RequestAttribute(AuthFilter.MEMBER_ID) long memberId) {
        try {
            authService.revokeWatchSessions(memberId);
        } catch (ProviderUnavailableException e) {
            throw new BusinessRuleException(ReasonCode.TEMPORARILY_UNAVAILABLE);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 게스트 세션 재개(v0.11 FR-197, D-89, 공개). 실패(형식 위조·비밀 불일치·연동한 회원·탈퇴 회원)는 사유를 구분하지 않고 401 본문 없음이다.
     * 접속지·자격 증명별 시도 제한을 넘으면 429.
     */
    @PostMapping("/auth/guest/session")
    ResponseEntity<TokenResponse> resumeGuest(
            @Valid @RequestBody GuestSessionRequest request, HttpServletRequest http) {
        AuthAttemptGuard.Attempt attempt = attempts.admit(http, request.guestCredential());
        try {
            return ResponseEntity.ok(
                    TokenResponse.of(
                            authService.resumeGuest(
                                    request.guestCredential(), LocalDateTime.now(clock))));
        } catch (AuthFailureException e) {
            attempts.failed(attempt);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * 게스트의 소셜 계정 연동(v0.11 FR-198, D-90). 경로는 /me 아래지만 인증 흐름이라 여기서 받는다(5.7.1). 제공자 인증 실패 401
     * AUTH_FAILED, 무응답 503 PROVIDER_UNAVAILABLE, 다른 회원과 연동된 계정 409 ALREADY_MEMBER, 이미 연동 409
     * ALREADY_LINKED, 시도 제한 429. 토큰은 바꾸지 않는다.
     */
    @PostMapping("/me/social-account")
    ResponseEntity<SocialLinkResponse> linkSocialAccount(
            @RequestAttribute(AuthFilter.MEMBER_ID) long memberId,
            @Valid @RequestBody SocialLinkRequest request,
            HttpServletRequest http) {
        AuthAttemptGuard.Attempt attempt =
                attempts.admit(http, request.provider() + ":" + request.providerToken());
        SocialLinkResult result;
        try {
            result =
                    authService.linkSocialAccount(
                            memberId, request.provider(), request.providerToken());
        } catch (AuthFailureException e) {
            attempts.failed(attempt);
            throw new BusinessRuleException(ReasonCode.AUTH_FAILED);
        } catch (ProviderUnavailableException e) {
            throw new BusinessRuleException(ReasonCode.PROVIDER_UNAVAILABLE);
        }
        return ResponseEntity.ok(new SocialLinkResponse(result.accountType(), result.provider()));
    }

    /**
     * 게스트 세션 재개 요청.
     *
     * @param guestCredential 게스트 시작 때 받은 기기 자격 증명(g1.&lt;memberId&gt;.&lt;secret&gt;)
     */
    record GuestSessionRequest(@NotBlank @Size(max = GUEST_CREDENTIAL_MAX) String guestCredential) {

        @Override
        public String toString() {
            return "GuestSessionRequest[guestCredential=***]";
        }
    }

    /**
     * 소셜 계정 연동 요청.
     *
     * @param provider KAKAO 또는 GOOGLE
     * @param providerToken 제공자 SDK가 준 토큰
     */
    record SocialLinkRequest(
            @NotNull ProviderType provider, @NotBlank @Size(max = TOKEN_MAX) String providerToken) {

        @Override
        public String toString() {
            return "SocialLinkRequest[provider=" + provider + ", providerToken=***]";
        }
    }

    /**
     * 연동 결과.
     *
     * @param accountType 연동 뒤 계정 유형(LINKED)
     * @param provider 연동한 제공자
     */
    record SocialLinkResponse(AccountType accountType, ProviderType provider) {}

    /**
     * 로그인 요청.
     *
     * @param provider KAKAO 또는 GOOGLE
     * @param providerToken 제공자 SDK가 준 토큰(카카오 접근 토큰, 구글 ID 토큰)
     */
    record LoginRequest(
            @NotNull ProviderType provider, @NotBlank @Size(max = TOKEN_MAX) String providerToken) {

        @Override
        public String toString() {
            return "LoginRequest[provider=" + provider + ", providerToken=***]";
        }
    }

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
