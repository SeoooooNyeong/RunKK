package com.a207.runkk.member;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import com.a207.runkk.common.Tier;
import com.a207.runkk.common.Timestamps;
import com.a207.runkk.policy.TierBoundary;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 회원 한 명의 계정 상태를 보관한다(설계 6.1.2, 상태도 2.4.9). 상태 변경은 업무 동사 메소드로만 하고 ACTIVE가 아니면
 * IllegalStateException을 던진다. 같은 회원 행을 티어 갱신과 설정 변경이 함께 고칠 수 있어 version 낙관적 잠금을 둔다(5.3).
 */
@Entity
@Table(name = "member")
public class Member {

    private static final int INTRO_MAX = 40;
    private static final long METERS_PER_LEVEL = 50_000;
    private static final int MIN_BIRTH_YEAR = 1900;
    private static final int MAX_BIRTH_YEAR = 9999;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded private SocialAccount account;

    @Embedded private Nickname nickname;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 12)
    private Gender gender;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "intro", length = INTRO_MAX)
    private String intro;

    /** 성향 태그 이름(쉼표 구분). 비면 태그 없음. */
    @Column(name = "tags", nullable = false, length = 200)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 10)
    private Tier tier;

    @Column(name = "level", nullable = false)
    private int level;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Embedded private MemberSettings settings;

    @Embedded private ConsentHistory consents;

    @Embedded private TierProgress tierProgress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private MemberStatus status;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Embedded private Timestamps timestamps;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Member() {}

    private Member(SocialAccount account, SignUpForm form, Nickname nickname, LocalDateTime now) {
        this.account = account;
        this.nickname = nickname;
        this.birthYear = requireBirthYear(form.birthYear());
        this.gender = form.gender();
        this.photoUrl = blankToNull(form.photoUrl());
        this.intro = requireIntro(form.intro());
        this.tags = encode(form.tagSet());
        this.tier = Tier.UNRANKED;
        this.level = 1;
        this.joinedAt = now;
        this.settings = MemberSettings.defaults(form.recordVisibility());
        this.consents = new ConsentHistory();
        for (Map.Entry<ConsentItem, Boolean> e : form.consents().entrySet()) {
            if (e.getKey().collectedAtSignUp()) {
                consents.record(e.getKey(), Boolean.TRUE.equals(e.getValue()), now);
            }
        }
        this.tierProgress = TierProgress.empty();
        this.status = MemberStatus.ACTIVE;
        this.timestamps = Timestamps.createdAt(now);
    }

    /** 가입. 설정 기본값(srs.md 6.3 1.2), 동의 이력 기록, 티어 UNRANKED, 레벨 1. form.validate는 서비스가 먼저 부른다. */
    public static Member signUp(
            SocialAccount account, SignUpForm form, Nickname nickname, LocalDateTime now) {
        if (account == null || form == null || nickname == null || now == null) {
            throw new IllegalArgumentException("가입 입력이 비었다");
        }
        return new Member(account, form, nickname, now);
    }

    /** 프로필 편집(FR-007). newNickname이 null이면 닉네임을 바꾸지 않는다. 닉네임 중복 검사는 ProfileService가 한다. */
    public void editProfile(ProfileEdit edit, Nickname newNickname) {
        requireActive();
        String newIntro = edit.intro() == null ? intro : requireIntro(edit.intro());
        String newTags =
                edit.tags() == null ? tags : encode(TagSet.of(edit.tags(), TagOwner.RUNNER));
        if (newNickname != null) {
            this.nickname = newNickname;
        }
        if (edit.birthYear() != null) {
            this.birthYear = requireBirthYear(edit.birthYear());
        }
        if (edit.gender() != null) {
            this.gender = edit.gender();
        }
        if (edit.photoUrl() != null) {
            this.photoUrl = blankToNull(edit.photoUrl());
        }
        this.intro = newIntro;
        this.tags = newTags;
    }

    /**
     * 동의가 아닌 설정 변경. 동의 항목이면 BusinessRuleException(CONSENT_ITEM_NOT_SETTING). 워치 연동을 켜는데 심박 동의가 없으면
     * 바꾸지 않고 needsConsent(HEART_RATE)(FR-019).
     */
    public SettingOutcome changeSetting(SettingChange change) {
        requireActive();
        if (change.isConsentChange()) {
            throw new BusinessRuleException(ReasonCode.CONSENT_ITEM_NOT_SETTING);
        }
        if (change.item() == SettingItem.WATCH_LINK
                && "true".equals(change.value())
                && !hasConsent(ConsentItem.HEART_RATE)) {
            return SettingOutcome.needsConsent(ConsentItem.HEART_RATE);
        }
        settings.apply(change);
        return SettingOutcome.changed();
    }

    /** 동의. 이력에 덧붙이고 이전·이후 값을 돌려준다(감사용). */
    public ConsentChange giveConsent(ConsentItem item, LocalDateTime now) {
        requireActive();
        boolean before = consents.isAgreed(item);
        consents.record(item, true, now);
        timestamps = timestamps.touched(now);
        return new ConsentChange(item, before, true, now);
    }

    /** 동의 철회. 약관·개인정보·만 14세 확인은 철회할 수 없다(IllegalArgumentException). */
    public ConsentChange withdrawConsent(ConsentItem item, LocalDateTime now) {
        requireActive();
        if (!item.isWithdrawable()) {
            throw new IllegalArgumentException("철회할 수 없는 동의: " + item);
        }
        boolean before = consents.isAgreed(item);
        consents.record(item, false, now);
        timestamps = timestamps.touched(now);
        return new ConsentChange(item, before, false, now);
    }

    public boolean hasConsent(ConsentItem item) {
        return consents.isAgreed(item);
    }

    /** 기본 대결 거리 저장. MemberSettingService만 부른다(FR-080). */
    public void rememberBattleDistance(StandardDistance d) {
        requireActive();
        settings.rememberBattleDistance(d);
    }

    /**
     * 유효 러닝 반영. tierProgress가 평가 평균을 주면 tier ← boundary.tierOf(평균). level ← 1 + ⌊누적 km ÷ 50⌋. 같은
     * 순번을 다시 받으면 아무것도 바꾸지 않는다(멱등, D-37).
     */
    public TierChange recordValidRun(RunScoreInput input, TierBoundary boundary) {
        requireActive();
        Tier previousTier = tier;
        int previousLevel = level;
        OptionalDouble average = tierProgress.addValidRun(input);
        if (average.isPresent()) {
            this.tier = boundary.tierOf(average.getAsDouble());
        }
        this.level = 1 + (int) (tierProgress.totalValidDistance().meters() / METERS_PER_LEVEL);
        return new TierChange(previousTier, tier, previousLevel, level);
    }

    /** 탈퇴. 식별정보와 동의 이력을 지운다(FR-021). 두 번째 호출은 IllegalStateException. */
    public void withdraw(LocalDateTime now) {
        requireActive();
        this.nickname = null;
        this.account = null;
        this.birthYear = null;
        this.gender = null;
        this.photoUrl = null;
        this.intro = null;
        this.consents.wipe();
        this.status = MemberStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.timestamps = timestamps.touched(now);
    }

    /**
     * 탈퇴 뒤 남은 개인 자료 정리. 설정은 기본값(비공개)으로, 티어 누계·태그는 비우고 id·status·withdrawnAt만 남긴다. 다시 불러도 결과가 같다.
     */
    public void purgeResidual() {
        if (status != MemberStatus.WITHDRAWN) {
            throw new IllegalStateException("탈퇴한 회원만 정리한다");
        }
        this.settings = MemberSettings.defaults(RecordVisibility.PRIVATE);
        this.tierProgress = TierProgress.empty();
        this.tags = "";
        this.tier = Tier.UNRANKED;
        this.level = 1;
    }

    /** year 기준 나이. 출생연도가 없으면 비어 있음. */
    public OptionalInt ageIn(int year) {
        return birthYear == null ? OptionalInt.empty() : OptionalInt.of(year - birthYear);
    }

    /** 표시명. 탈퇴했으면 DisplayName.WITHDRAWN. */
    public DisplayName displayName() {
        if (status == MemberStatus.WITHDRAWN || nickname == null) {
            return DisplayName.WITHDRAWN;
        }
        return DisplayName.of(nickname.value());
    }

    public Long getId() {
        return id;
    }

    public Tier getTier() {
        return tier;
    }

    public int getLevel() {
        return level;
    }

    public TagSet getTags() {
        return decode(tags);
    }

    /** 불변 사본. 고쳐도 Member는 바뀌지 않는다(design-review.md A44). */
    public MemberSettingsView getSettings() {
        Map<ConsentItem, Boolean> agreed = new EnumMap<>(ConsentItem.class);
        for (ConsentItem item : ConsentItem.values()) {
            agreed.put(item, consents.isAgreed(item));
        }
        return settings.view(agreed, birthYear);
    }

    public MemberStatus getStatus() {
        return status;
    }

    SocialAccount account() {
        return account;
    }

    Nickname nickname() {
        return nickname;
    }

    Integer birthYear() {
        return birthYear;
    }

    Gender gender() {
        return gender;
    }

    String photoUrl() {
        return photoUrl;
    }

    String intro() {
        return intro;
    }

    LocalDateTime withdrawnAt() {
        return withdrawnAt;
    }

    boolean allowsPush(String notificationType) {
        return settings.allowsPush(notificationType);
    }

    ProfileVisibility profileVisibility() {
        return settings.profileVisibility();
    }

    MemberPreference preference() {
        return new MemberPreference(
                settings.unit(),
                settings.recordVisibility(),
                settings.courseMapShared(),
                settings.profileVisibility(),
                consents.isAgreed(ConsentItem.LLM_TRANSFER));
    }

    private void requireActive() {
        if (status != MemberStatus.ACTIVE) {
            throw new IllegalStateException("탈퇴한 회원이다");
        }
    }

    private static String requireIntro(String value) {
        String v = blankToNull(value);
        if (v != null && v.length() > INTRO_MAX) {
            throw new BusinessRuleException(ReasonCode.INTRO_TOO_LONG);
        }
        return v;
    }

    private static Integer requireBirthYear(Integer year) {
        if (year != null && (year < MIN_BIRTH_YEAR || year > MAX_BIRTH_YEAR)) {
            throw new IllegalArgumentException("출생연도는 4자리: " + year);
        }
        return year;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** 쉼표 구분 태그 이름 → TagSet. infra.member도 같은 형식으로 읽는다. */
    static TagSet decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return TagSet.empty(TagOwner.RUNNER);
        }
        Set<PersonalityTag> set =
                Arrays.stream(encoded.split(","))
                        .map(PersonalityTag::valueOf)
                        .collect(Collectors.toSet());
        return TagSet.of(set, TagOwner.RUNNER);
    }

    private static String encode(TagSet tagSet) {
        return tagSet.tags().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
}
