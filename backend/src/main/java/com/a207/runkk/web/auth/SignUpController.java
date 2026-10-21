package com.a207.runkk.web.auth;

import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.Gender;
import com.a207.runkk.member.SignUpCommand;
import com.a207.runkk.member.SignUpForm;
import com.a207.runkk.member.SignUpResult;
import com.a207.runkk.member.SignUpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 가입(설계 5.7.1: POST /members → SignUpService.signUp, FR-003, FR-004, FR-028). 거부는
 * BusinessRuleException(사유)을 ApiExceptionHandler가 409로 바꾼다.
 */
@RestController
@RequestMapping("/api/v1/members")
public class SignUpController {

    private static final int TICKET_MAX = 4096;
    private static final int TEXT_MAX = 500;

    private final SignUpService signUpService;

    public SignUpController(SignUpService signUpService) {
        this.signUpService = signUpService;
    }

    @PostMapping
    ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        SignUpResult result =
                signUpService.signUp(new SignUpCommand(request.signUpTicket(), request.toForm()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        new SignUpResponse(
                                result.memberId(),
                                result.nickname(),
                                result.tokens().accessToken(),
                                result.tokens().refreshToken()));
    }

    /**
     * 가입 요청(자료사전 회원가입요청).
     *
     * @param signUpTicket 로그인 때 받은 가입 티켓
     * @param nickname 닉네임(없으면 서버가 정한다)
     * @param birthYear 출생연도
     * @param gender 성별
     * @param photoUrl 사진 주소
     * @param intro 소개(40자 이하)
     * @param consents 동의 항목 → 동의 여부
     * @param recordVisibility 기록 공개 범위(기본 PUBLIC)
     * @param tags 성향 태그
     */
    record SignUpRequest(
            @NotBlank @Size(max = TICKET_MAX) String signUpTicket,
            @Size(max = TEXT_MAX) String nickname,
            Integer birthYear,
            Gender gender,
            @Size(max = TEXT_MAX) String photoUrl,
            @Size(max = TEXT_MAX) String intro,
            @NotNull Map<ConsentItem, Boolean> consents,
            RecordVisibility recordVisibility,
            Set<PersonalityTag> tags) {

        SignUpForm toForm() {
            return new SignUpForm(
                    nickname, birthYear, gender, photoUrl, intro, consents, recordVisibility, tags);
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
