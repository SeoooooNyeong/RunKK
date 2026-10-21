package com.a207.runkk.web.me;

import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.ConsentResult;
import com.a207.runkk.member.DocumentType;
import com.a207.runkk.member.Gender;
import com.a207.runkk.member.LegalDocument;
import com.a207.runkk.member.MemberPreference;
import com.a207.runkk.member.MemberProfile;
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
import com.a207.runkk.profile.ProfileQueryService;
import com.a207.runkk.profile.ProfileView;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
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
 * memberId다. GET /members/{id}/profile은 profile.ProfileQueryService가 조립하고 공개 수준(D-52)에 따라 거른 항목만
 * 준다.
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
    private final ProfileQueryService profileQueryService;

    public MeController(
            ProfileService profileService,
            MemberSettingService memberSettingService,
            WithdrawalService withdrawalService,
            ProfileQueryService profileQueryService) {
        this.profileService = profileService;
        this.memberSettingService = memberSettingService;
        this.withdrawalService = withdrawalService;
        this.profileQueryService = profileQueryService;
    }

    /** 회원 프로필(FR-005, FR-006). 공개 수준(SELF·PUBLIC·PRIVATE·WITHDRAWN)이 정한 항목만 담긴다. 없는 회원은 400. */
    @GetMapping("/members/{id}/profile")
    ProfileResponse profile(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long targetId) {
        return ProfileResponse.of(profileQueryService.view(memberId, targetId));
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

    /**
     * 확인 단계는 앱 화면이 하고 서버는 확인된 요청만 받는다(D-36). 가드가 거부하면 409 공통 오류 응답(code: LEADER_MUST_TRANSFER ·
     * BATTLE_IN_PROGRESS).
     */
    @DeleteMapping("/me")
    WithdrawalResult withdraw(@RequestAttribute(MEMBER_ID) long memberId) {
        return withdrawalService.withdraw(memberId);
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

    /**
     * 프로필 응답(앱 api.me 형식). 공개 수준이 감춘 항목은 null·빈 값이다. disclosure는 member.DisclosureLevel
     * 이름(SELF·PUBLIC· PRIVATE·WITHDRAWN) 그대로다.
     *
     * @param memberId 회원 ID
     * @param displayName 표시명
     * @param disclosure 공개 수준
     * @param tier 티어(UNRANKED·BRONZE·SILVER·GOLD·PLATINUM·MASTER)
     * @param level 레벨
     * @param tags 성향 태그
     * @param intro 소개
     * @param photoUrl 프로필 사진 주소
     * @param ageBand 나이대("20대")
     * @param gender 성별
     * @param runimalType Runimal 유형
     * @param faceKey 아바타 얼굴 키
     * @param crewName 소속 크루명
     * @param crew 소속 크루(ID·이름·로고)
     * @param longestDistanceMeters 최장 거리(본인만)
     * @param settings 설정(본인만)
     */
    record ProfileResponse(
            long memberId,
            String displayName,
            String disclosure,
            String tier,
            Integer level,
            Set<PersonalityTag> tags,
            String intro,
            String photoUrl,
            String ageBand,
            Gender gender,
            String runimalType,
            String faceKey,
            String crewName,
            ProfileView.Crew crew,
            Long longestDistanceMeters,
            MemberPreference settings) {

        static ProfileResponse of(ProfileView v) {
            MemberProfile about = v.about();
            return new ProfileResponse(
                    v.memberId(),
                    v.displayName().value(),
                    v.disclosure().name(),
                    v.tier() == null ? null : v.tier().name(),
                    about == null ? null : about.level(),
                    v.tags(),
                    about == null ? null : about.intro(),
                    about == null ? null : about.photoUrl(),
                    about == null ? null : about.ageBand(),
                    about == null ? null : about.gender(),
                    v.runimalType(),
                    v.faceKey(),
                    v.crew() == null ? null : v.crew().name(),
                    v.crew(),
                    v.longestDistance() == null ? null : v.longestDistance().meters(),
                    v.settings());
        }
    }
}
