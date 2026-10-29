package com.a207.runkk.web.auth;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.GuestStartForm;
import com.a207.runkk.member.GuestStartResult;
import com.a207.runkk.member.SignUpCommand;
import com.a207.runkk.member.SignUpForm;
import com.a207.runkk.member.SignUpResult;
import com.a207.runkk.member.SignUpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 가입(설계 5.7.1: POST /members → SignUpService.signUp, FR-003, FR-004, FR-010, 요청 축소 D-90)과 v0.11
 * 게스트 시작(POST /members/guest, FR-197). 거부는 BusinessRuleException(사유)을 ApiExceptionHandler가 409로
 * 바꾼다.
 */
@RestController
@RequestMapping("/api/v1/members")
public class SignUpController {

    private static final int TICKET_MAX = 4096;

    /** 닉네임은 서버가 고치므로(D-143) 길이 제약 없이 요청 크기 한도만 둔다. */
    private static final int TEXT_MAX = 500;

    private final SignUpService signUpService;
    private final AuthAttemptGuard attempts;

    public SignUpController(SignUpService signUpService, AuthAttemptGuard attempts) {
        this.signUpService = signUpService;
        this.attempts = attempts;
    }

    /** 접속지·가입 티켓별 시도 제한을 넘으면 429. 위조·만료 티켓(SIGN_UP_TICKET_INVALID)을 실패로 센다(M5). */
    @PostMapping
    ResponseEntity<SignUpResponse> signUp(
            @Valid @RequestBody SignUpRequest request, HttpServletRequest http) {
        AuthAttemptGuard.Attempt attempt = attempts.admit(http, request.signUpTicket());
        SignUpResult result;
        try {
            result =
                    signUpService.signUp(
                            new SignUpCommand(request.signUpTicket(), request.toForm()));
        } catch (BusinessRuleException e) {
            if (e.reason() == ReasonCode.SIGN_UP_TICKET_INVALID) {
                attempts.failed(attempt);
            }
            throw e;
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        new SignUpResponse(
                                result.memberId(),
                                result.nickname(),
                                result.tokens().accessToken(),
                                result.tokens().refreshToken()));
    }

    /**
     * 게스트로 시작(v0.11 FR-197, D-89, 공개). 접속지별 게스트 생성 한도(기본 1시간 10회)를 넘으면 429. 받지 않는 동의 항목은 400, 필수 동의
     * 거부는 409(CONSENT_MISSING·UNDER_14), 기본 닉네임 소진은 503(TEMPORARILY_UNAVAILABLE).
     */
    @PostMapping("/guest")
    ResponseEntity<GuestStartResponse> startAsGuest(
            @Valid @RequestBody GuestStartRequest request, HttpServletRequest http) {
        AuthAttemptGuard.Attempt attempt = attempts.admitGuestStart(http);
        GuestStartResult result;
        try {
            result = signUpService.startAsGuest(new GuestStartForm(request.consents()));
        } catch (IllegalArgumentException e) {
            attempts.guestRejected(attempt);
            throw e;
        } catch (BusinessRuleException e) {
            if (e.reason() != ReasonCode.TEMPORARILY_UNAVAILABLE) {
                attempts.guestRejected(attempt);
            }
            throw e;
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        new GuestStartResponse(
                                result.memberId(),
                                result.nickname(),
                                result.tokens().accessToken(),
                                result.tokens().refreshToken(),
                                result.guestCredential()));
    }

    /**
     * 게스트 시작 요청. 필수 동의 AGE_14_PLUS·TERMS·PRIVACY·LOCATION, 선택 MARKETING·LLM_TRANSFER(빠지면 false).
     *
     * @param consents 동의 항목 → 동의 여부
     */
    record GuestStartRequest(@NotNull Map<ConsentItem, Boolean> consents) {}

    /**
     * 게스트 시작 결과. guestCredential은 다시 내주지 않는다(D-89).
     *
     * @param memberId 회원 ID
     * @param nickname 기본 닉네임("러너" + 숫자 4자리)
     * @param accessToken 접근 토큰
     * @param refreshToken 갱신 토큰
     * @param guestCredential 기기 자격 증명
     */
    record GuestStartResponse(
            long memberId,
            String nickname,
            String accessToken,
            String refreshToken,
            String guestCredential) {

        @Override
        public String toString() {
            return "GuestStartResponse[memberId=" + memberId + "]";
        }
    }

    /**
     * 가입 요청(자료사전 회원가입요청, v0.11 D-90). 출생연도·성별·사진·소개·공개 범위·태그는 지웠다(보내면 무시).
     *
     * @param signUpTicket 로그인 때 받은 가입 티켓
     * @param nickname 닉네임(제공자 이름, 없으면 서버가 "러너" + 숫자로 정한다)
     * @param consents 동의 항목 → 동의 여부
     */
    record SignUpRequest(
            @NotBlank @Size(max = TICKET_MAX) String signUpTicket,
            @Size(max = TEXT_MAX) String nickname,
            @NotNull Map<ConsentItem, Boolean> consents) {

        SignUpForm toForm() {
            return new SignUpForm(nickname, consents);
        }
    }

    /**
     * 가입 완료.
     *
     * @param memberId 회원 ID
     * @param nickname 확정된 닉네임
     * @param accessToken 접근 토큰
     * @param refreshToken 갱신 토큰
     */
    record SignUpResponse(
            long memberId, String nickname, String accessToken, String refreshToken) {}
}
