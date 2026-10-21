package com.a207.runkk.web.me;

import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.ConsentResult;
import com.a207.runkk.member.DocumentType;
import com.a207.runkk.member.Gender;
import com.a207.runkk.member.LegalDocument;
import com.a207.runkk.member.MemberSettingService;
import com.a207.runkk.member.MemberSettingsView;
import com.a207.runkk.member.ProfileEdit;
import com.a207.runkk.member.ProfileEditResult;
import com.a207.runkk.member.ProfileService;
import com.a207.runkk.member.SettingChange;
import com.a207.runkk.member.SettingItem;
import com.a207.runkk.member.SettingResult;
import com.a207.runkk.member.WithdrawalResult;
import com.a207.runkk.member.WithdrawalService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인 계정·설정·동의·탈퇴와 법정 고지 문서(설계 5.7.1, web.me → member). 회원 ID는 web.auth.AuthFilter가 붙인 요청 속성
 * memberId다. GET /members/{id}/profile(ProfileQueryService, profile 패키지)은 runimal·avatar·crew가 필요해
 * 다음 단계에서 이 컨트롤러에 더한다.
 */
@RestController
@RequestMapping("/api/v1")
public class MeController {

    private static final String MEMBER_ID = "memberId";
    private static final int TEXT_MAX = 500;
    private static final int VALUE_MAX = 2_000;

    private final ProfileService profileService;
    private final MemberSettingService memberSettingService;
    private final WithdrawalService withdrawalService;

    public MeController(
            ProfileService profileService,
            MemberSettingService memberSettingService,
            WithdrawalService withdrawalService) {
        this.profileService = profileService;
        this.memberSettingService = memberSettingService;
        this.withdrawalService = withdrawalService;
    }

    @PatchMapping("/me/profile")
    ProfileEditResult editProfile(
            @RequestAttribute(MEMBER_ID) long memberId,
            @Valid @RequestBody ProfileEditRequest request) {
        return profileService.editProfile(memberId, request.toEdit());
    }

    @GetMapping("/me/settings")
    MemberSettingsView settings(@RequestAttribute(MEMBER_ID) long memberId) {
        return memberSettingService.settingsOf(memberId);
    }

    @PatchMapping("/me/settings")
    SettingResult changeSetting(
            @RequestAttribute(MEMBER_ID) long memberId,
            @Valid @RequestBody SettingChangeRequest request) {
        return memberSettingService.changeSetting(memberId, request.toChange());
    }

    @PutMapping("/me/consents/{item}")
    ConsentResult giveConsent(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable ConsentItem item) {
        return memberSettingService.giveConsent(memberId, item);
    }

    @DeleteMapping("/me/consents/{item}")
    ConsentResult withdrawConsent(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable ConsentItem item) {
        return memberSettingService.withdrawConsent(memberId, item);
    }

    @GetMapping("/legal/{type}")
    LegalDocument legalDocument(@PathVariable DocumentType type) {
        return memberSettingService.legalDocument(type);
    }

    /** 확인 단계는 앱 화면이 하고 서버는 확인된 요청만 받는다(D-36). 가드가 거부하면 409 + 사유. */
    @DeleteMapping("/me")
    ResponseEntity<WithdrawalResult> withdraw(@RequestAttribute(MEMBER_ID) long memberId) {
        WithdrawalResult result = withdrawalService.withdraw(memberId);
        return result.withdrawn()
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    /**
     * 프로필 변경 요청(null 항목은 그대로).
     *
     * @param nickname 닉네임
     * @param birthYear 출생연도
     * @param gender 성별
     * @param photoUrl 사진 주소(빈 문자열이면 지움)
     * @param intro 소개(빈 문자열이면 지움)
     * @param tags 성향 태그
     */
    record ProfileEditRequest(
            @Size(max = TEXT_MAX) String nickname,
            Integer birthYear,
            Gender gender,
            @Size(max = TEXT_MAX) String photoUrl,
            @Size(max = TEXT_MAX) String intro,
            Set<PersonalityTag> tags) {

        ProfileEdit toEdit() {
            return new ProfileEdit(nickname, birthYear, gender, photoUrl, intro, tags);
        }
    }

    /**
     * 설정 변경 요청(동의가 아닌 항목만). value는 문자열이거나, 러닝설정·대결설정이면 JSON 객체다.
     *
     * @param item 설정 항목
     * @param value 설정 값
     * @param notificationType 알림 종류(ALERT일 때)
     */
    record SettingChangeRequest(
            @NotNull SettingItem item, @NotNull JsonNode value, String notificationType) {

        SettingChange toChange() {
            String text = value.isTextual() ? value.asText() : value.toString();
            if (text.length() > VALUE_MAX) {
                throw new IllegalArgumentException("설정 값이 너무 길다");
            }
            return new SettingChange(item, text, notificationType);
        }
    }
}
