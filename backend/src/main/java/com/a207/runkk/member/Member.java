package com.a207.runkk.member;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.StandardDistance;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * 회원 한 명의 계정 상태를 보관한다(설계 6.1.2, 상태도 2.4.9). 상태 변경은 업무 동사 메소드로만 하고 ACTIVE가 아니면
 * IllegalStateException을 던진다. 같은 회원 행을 티어 갱신과 설정 변경이 함께 고칠 수 있어 version 낙관적 잠금을 둔다(5.3). v0.11:
 * 게스트(accountType GUEST, 기기 자격 증명 해시)와 소셜 연동(GUEST → LINKED 한 방향), 마지막 접속일시를 더하고 러너 성향 태그를
 * 지웠다(D-89, D-90, D-91).
 */
@Entity
@Table(name = "member")
public class Member {

    private static final int INTRO_MAX = 40;
    private static final long METERS_PER_LEVEL = 50_000;
    private static final int MIN_BIRTH_YEAR = 1900;
    private static final int MAX_BIRTH_YEAR = 9999;
    private static final int MIN_AGE_EXCLUSIVE = 14;
    private static final Pattern HASH = Pattern.compile("^[0-9a-f]{64}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded private SocialAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10)
    private AccountType accountType;

    /** 게스트 기기 자격 증명 비밀의 SHA-256 16진 64자. 게스트만, 연동·탈퇴 때 지운다(D-89). */
    @Column(name = "guest_credential_hash", length = 64)
    private String guestCredentialHash;

    /** 마지막 접속일시. 날짜가 바뀐 때만, 앞으로만 바꾼다(FR-200, D-89, D-164). */
    @Column(name = "last_access_at", nullable = false)
    private LocalDateTime lastAccessAt;

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

    private Member(
            SocialAccount account,
            Nickname nickname,
            Map<ConsentItem, Boolean> consents,
            LocalDateTime now) {
        this.account = account;
        this.accountType = account == null ? AccountType.GUEST : AccountType.LINKED;
        this.nickname = nickname;
        this.lastAccessAt = now;
        this.tier = Tier.UNRANKED;
        this.level = 1;
        this.joinedAt = now;
        this.settings = MemberSettings.defaults(RecordVisibility.PUBLIC);
        this.consents = new ConsentHistory();
        for (Map.Entry<ConsentItem, Boolean> e : RequiredConsents.atSignUp(consents).entrySet()) {
            this.consents.record(e.getKey(), e.getValue(), now);
        }
        this.tierProgress = TierProgress.empty();
        this.status = MemberStatus.ACTIVE;
        this.timestamps = Timestamps.createdAt(now);
    }

    /**
     * 가입. 설정 기본값(srs.md 6.3 1.2, 기록 공개 범위 PUBLIC, FR-028), 동의 이력 기록, 티어 UNRANKED, 레벨 1, LINKED,
     * lastAccessAt = now. form.validate는 서비스가 먼저 부른다.
     */
    public static Member signUp(
            SocialAccount account, SignUpForm form, Nickname nickname, LocalDateTime now) {
        if (account == null || form == null || nickname == null || now == null) {
            throw new IllegalArgumentException("가입 입력이 비었다");
        }
        return new Member(account, nickname, form.consents(), now);
    }

    /**
     * 게스트 시작(FR-197, D-89). 소셜 계정 없음, GUEST, 자격 증명 해시는 저장 뒤 attachGuestCredential로 붙인다(D-128). 설정
     * 기본값·동의 이력은 signUp과 같다. GuestStartForm.validate는 서비스가 먼저 부른다.
     */
    public static Member guest(
            Nickname nickname, Map<ConsentItem, Boolean> consents, LocalDateTime now) {
        if (nickname == null || consents == null || now == null) {
            throw new IllegalArgumentException("게스트 입력이 비었다");
        }
        return new Member(null, nickname, consents, now);
    }

    /**
     * 게스트 자격 증명 해시를 붙인다(D-128). 게스트이고 해시가 비어 있을 때만. 이미 있거나 연동 회원이면 IllegalStateException, 해시 형식(16진
     * 64자)이 아니면 IllegalArgumentException.
     */
    public void attachGuestCredential(String credentialHash) {
        requireActive();
        if (accountType != AccountType.GUEST || guestCredentialHash != null) {
            throw new IllegalStateException("자격 증명은 해시 없는 게스트에 한 번만 붙인다");
        }
        if (credentialHash == null || !HASH.matcher(credentialHash).matches()) {
            throw new IllegalArgumentException("자격 증명 해시는 16진 64자");
        }
        this.guestCredentialHash = credentialHash;
    }

    /**
     * 소셜 계정 연동(FR-198, D-90). 이미 LINKED면 BusinessRuleException(ALREADY_LINKED). 회원 ID는 그대로이고 게스트 자격
     * 증명 해시를 지운다. LINKED에서 GUEST로 돌아가는 연산은 없다.
     */
    public void linkSocialAccount(SocialAccount newAccount) {
        requireActive();
        if (newAccount == null) {
            throw new IllegalArgumentException("연동할 계정이 비었다");
        }
        if (accountType == AccountType.LINKED) {
            throw new BusinessRuleException(ReasonCode.ALREADY_LINKED);
        }
        this.account = newAccount;
        this.accountType = AccountType.LINKED;
        this.guestCredentialHash = null;
    }

    /** 게스트 비밀 확인. 게스트이고 해시가 같으면 참. 비교는 MessageDigest.isEqual(상수 시간, D-89). */
    public boolean matchesGuestSecret(String secret) {
        if (accountType != AccountType.GUEST || guestCredentialHash == null || secret == null) {
            return false;
        }
        return MessageDigest.isEqual(
                GuestCredential.hashOf(secret).getBytes(StandardCharsets.US_ASCII),
                guestCredentialHash.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * 마지막 접속 기록(FR-200, D-89 (3), D-164 (1)). now가 lastAccessAt보다 늦고 날짜가 다를 때만 바꾸고 참. 같은 날이거나 더 이른
     * 시각이면 거짓(쓰기 없음).
     */
    public boolean recordAccess(LocalDateTime now) {
        requireActive();
        if (!now.isAfter(lastAccessAt) || now.toLocalDate().equals(lastAccessAt.toLocalDate())) {
            return false;
        }
        this.lastAccessAt = now;
        return true;
    }

    /** 게스트인가(FR-199). */
    public boolean isGuest() {
        return accountType == AccountType.GUEST;
    }

    /**
     * 프로필 편집(FR-007). newNickname이 null이면 닉네임을 바꾸지 않는다. 닉네임 중복 검사는 ProfileService가 한다. 출생연도 형식
     * 오류(4자리 숫자 아님)는 IllegalArgumentException(400), (currentYear − 출생연도) ≤ 14면 UNDER_14(409,
     * FR-004, D-155 (2)). 거부되면 아무것도 바꾸지 않는다.
     */
    public void editProfile(ProfileEdit edit, Nickname newNickname, int currentYear) {
        requireActive();
        String newIntro = edit.intro() == null ? intro : requireIntro(edit.intro());
        if (edit.birthYear() != null) {
            requireAtLeast15(requireBirthYear(edit.birthYear()), currentYear);
        }
        if (newNickname != null) {
            this.nickname = newNickname;
        }
        if (edit.birthYear() != null) {
            this.birthYear = edit.birthYear();
        }
        if (edit.gender() != null) {
            this.gender = edit.gender();
        }
        if (edit.photoUrl() != null) {
            this.photoUrl = blankToNull(edit.photoUrl());
        }
        this.intro = newIntro;
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

    /**
     * 늦게 온 유효 러닝 반영(총괄 결정 7-3). 티어는 바꾸지 않고 누적 거리로 레벨만 다시 센다. running.TierReconcileService가 기록별 반영
     * 표시로 찾은 구멍에만 쓴다.
     */
    public TierChange recordLateValidRun(RunScoreInput input) {
        requireActive();
        int previousLevel = level;
        tierProgress.addLateValidRun(input);
        this.level = 1 + (int) (tierProgress.totalValidDistance().meters() / METERS_PER_LEVEL);
        return new TierChange(tier, tier, previousLevel, level);
    }

    /** 탈퇴. 식별정보·게스트 자격 증명 해시·동의 이력을 지운다(FR-021, D-89). 두 번째 호출은 IllegalStateException. */
    public void withdraw(LocalDateTime now) {
        requireActive();
        this.nickname = null;
        this.account = null;
        this.guestCredentialHash = null;
        this.birthYear = null;
        this.gender = null;
        this.photoUrl = null;
        this.intro = null;
        this.consents.wipe();
        this.status = MemberStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.timestamps = timestamps.touched(now);
    }

    /** 탈퇴 뒤 남은 개인 자료 정리. 설정은 기본값(비공개)으로, 티어 누계는 비우고 id·status·withdrawnAt만 남긴다. 다시 불러도 결과가 같다. */
    public void purgeResidual() {
        if (status != MemberStatus.WITHDRAWN) {
            throw new IllegalStateException("탈퇴한 회원만 정리한다");
        }
        this.settings = MemberSettings.defaults(RecordVisibility.PRIVATE);
        this.tierProgress = TierProgress.empty();
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

    /** 티어에 반영한 유효 러닝 순번(MemberQueryService.appliedValidRunCount, D-108). */
    int appliedValidRunCount() {
        return tierProgress.validRunCount();
    }

    public Tier getTier() {
        return tier;
    }

    public int getLevel() {
        return level;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    /** 불변 사본. 고쳐도 Member는 바뀌지 않는다(design-review.md A44). 계정 유형·연동 제공자는 이 본인 전용 사본에만 있다(FR-006). */
    public MemberSettingsView getSettings() {
        Map<ConsentItem, Boolean> agreed = new EnumMap<>(ConsentItem.class);
        for (ConsentItem item : ConsentItem.values()) {
            agreed.put(item, consents.isAgreed(item));
        }
        return settings.view(
                agreed, birthYear, accountType, account == null ? null : account.provider());
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

    LocalDateTime lastAccessAt() {
        return lastAccessAt;
    }

    String guestCredentialHash() {
        return guestCredentialHash;
    }

    List<ConsentRecord> consentRecords() {
        return consents.records();
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

    private static int requireBirthYear(Integer year) {
        if (year < MIN_BIRTH_YEAR || year > MAX_BIRTH_YEAR) {
            throw new IllegalArgumentException("출생연도는 4자리: " + year);
        }
        return year;
    }

    private static void requireAtLeast15(int year, int currentYear) {
        if (currentYear - year <= MIN_AGE_EXCLUSIVE) {
            throw new BusinessRuleException(ReasonCode.UNDER_14);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
