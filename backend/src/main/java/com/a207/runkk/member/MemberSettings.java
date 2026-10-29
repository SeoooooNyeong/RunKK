package com.a207.runkk.member;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DistanceUnit;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.StandardDistance;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 회원 설정 값을 보관한다(설계 6.1.2). 알림 수신 설정(notificationPrefs)은 알림 종류 이름별 켜짐 여부이고 기본은 모두 켜짐이다. 저장은 꺼진 종류
 * 이름만 쉼표로 이어 한 열에 둔다.
 */
@Embeddable
public class MemberSettings {

    /**
     * notification.NotificationType 21개의 이름(자료사전 알림종류 12개 + v0.11 9개, 부록 B, 06-05). member는
     * notification에 의존할 수 없어(12.2, notification → member) 이름 목록을 여기 둔다. 두 목록이 같은지는 notification의
     * 시험이 확인한다.
     */
    public static final List<String> NOTIFICATION_TYPES =
            List.of(
                    "PLAN_MISSED",
                    "BODY_SHAPE_CHANGED",
                    "JOIN_REQUESTED",
                    "JOIN_DECIDED",
                    "JOIN_EXPIRED",
                    "LEADER_TRANSFERRED",
                    "CREW_DELETED",
                    "KICKED",
                    "CREW_RUN_RECRUIT",
                    "CREW_RUN_SOON",
                    "OPPONENT_ARRIVED",
                    "BATTLE_CORRECTED",
                    "CREW_NOTICE",
                    "CREW_POLL",
                    "POLL_CLOSING_SOON",
                    "CREW_SCHEDULE",
                    "CREW_CHAT",
                    "CREW_GOAL_ACHIEVED",
                    "MANAGER_CHANGED",
                    "BACKGROUND_UNLOCKED",
                    "REPORT_RESOLVED");

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 5)
    private DistanceUnit unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_visibility", nullable = false, length = 10)
    private RecordVisibility recordVisibility;

    @Column(name = "course_map_shared", nullable = false)
    private boolean courseMapShared;

    /**
     * 옛 설정 항목 PROFILE_VISIBILITY 값(옛 앱 호환, v0.14 D-241). 열이 NOT NULL로 남아 있어 함께 쓴다: 두 항목이 모두 공개면
     * PUBLIC, 아니면 PRIVATE. 공개 판정에는 쓰지 않는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "profile_visibility", nullable = false, length = 10)
    private ProfileVisibility profileVisibility;

    /** 나이대 공개 여부(FR-006, FR-012, v0.14 D-241). */
    @Enumerated(EnumType.STRING)
    @Column(name = "age_band_visibility", nullable = false, length = 10)
    private ProfileVisibility ageBandVisibility;

    /** 성별 공개 여부(FR-006, FR-012, v0.14 D-241). */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender_visibility", nullable = false, length = 10)
    private ProfileVisibility genderVisibility;

    /** 꺼진 알림 종류 이름(쉼표 구분). 비면 모두 켜짐. */
    @Column(name = "muted_notification_types", nullable = false, length = 400)
    private String mutedNotificationTypes;

    @Column(name = "watch_linked", nullable = false)
    private boolean watchLinked;

    @Embedded private RunSettings runSettings;

    @Embedded private BattleSettings battleSettings;

    @Enumerated(EnumType.STRING)
    @Column(name = "screen_theme", nullable = false, length = 10)
    private ScreenTheme theme;

    protected MemberSettings() {}

    private MemberSettings(RecordVisibility chosen) {
        this.unit = DistanceUnit.KM;
        this.recordVisibility = chosen;
        this.courseMapShared = false;
        this.profileVisibility = ProfileVisibility.PRIVATE;
        this.ageBandVisibility = ProfileVisibility.PRIVATE;
        this.genderVisibility = ProfileVisibility.PRIVATE;
        this.mutedNotificationTypes = "";
        this.watchLinked = false;
        this.runSettings = RunSettings.defaults();
        this.battleSettings = BattleSettings.defaults();
        this.theme = ScreenTheme.SYSTEM;
    }

    /** 가입 기본값(srs.md 6.3 1.2). 가입·게스트 시작 모두 PUBLIC을 넘긴다(FR-010). 탈퇴 정리는 PRIVATE. */
    public static MemberSettings defaults(RecordVisibility chosen) {
        if (chosen == null) {
            throw new IllegalArgumentException("기록 공개 범위가 비었다");
        }
        return new MemberSettings(chosen);
    }

    /** 설정 하나를 바꾼다. 동의 항목이거나 허용 값이 아니면 BusinessRuleException. */
    public void apply(SettingChange change) {
        if (change.isConsentChange()) {
            throw new BusinessRuleException(ReasonCode.CONSENT_ITEM_NOT_SETTING);
        }
        change.item().validate(change.value());
        change.item().applyTo(this, change);
    }

    /** 그 종류의 푸시를 보내도 되는가(FR-175). */
    public boolean allowsPush(String notificationType) {
        return !muted().contains(notificationType);
    }

    /** 기본 대결 거리 저장(FR-080, N-10). Member.rememberBattleDistance만 부른다. */
    public void rememberBattleDistance(StandardDistance d) {
        if (d == null || !d.isRandomBattle()) {
            throw new IllegalArgumentException("랜덤 대결 거리가 아니다: " + d);
        }
        this.battleSettings = battleSettings.withDefaultDistance(d);
    }

    /** 화면 테마 변경(FR-203, D-92). SettingItem.THEME.applyTo가 부른다. */
    public void changeTheme(ScreenTheme t) {
        if (t == null) {
            throw new IllegalArgumentException("화면 테마가 비었다");
        }
        this.theme = t;
    }

    /** 모든 항목을 복사한 읽기 전용 사본. 동의·출생연도·계정 유형·연동 제공자는 회원이 넘긴다(GET /me/settings, D-53, D-90). */
    MemberSettingsView view(
            Map<ConsentItem, Boolean> consents,
            Integer birthYear,
            AccountType accountType,
            ProviderType linkedProvider) {
        Set<String> muted = muted();
        Map<String, Boolean> prefs = new LinkedHashMap<>();
        for (String type : NOTIFICATION_TYPES) {
            prefs.put(type, !muted.contains(type));
        }
        return new MemberSettingsView(
                unit,
                recordVisibility,
                courseMapShared,
                profileVisibility,
                Collections.unmodifiableMap(prefs),
                watchLinked,
                runSettings,
                battleSettings,
                consents,
                birthYear,
                theme,
                accountType,
                linkedProvider);
    }

    RecordVisibility recordVisibility() {
        return recordVisibility;
    }

    ProfileVisibility profileVisibility() {
        return profileVisibility;
    }

    DistanceUnit unit() {
        return unit;
    }

    boolean courseMapShared() {
        return courseMapShared;
    }

    void changeNotificationPref(String notificationType, boolean on) {
        if (!NOTIFICATION_TYPES.contains(notificationType)) {
            throw new BusinessRuleException(ReasonCode.SETTING_INVALID);
        }
        Set<String> muted = new TreeSet<>(muted());
        if (on) {
            muted.remove(notificationType);
        } else {
            muted.add(notificationType);
        }
        this.mutedNotificationTypes = String.join(",", muted);
    }

    void changeUnit(DistanceUnit unit) {
        this.unit = unit;
    }

    void changeRecordVisibility(RecordVisibility visibility) {
        this.recordVisibility = visibility;
    }

    void changeCourseMapShared(boolean shared) {
        this.courseMapShared = shared;
    }

    /** 옛 앱의 PROFILE_VISIBILITY 변경: 나이대·성별 두 항목에 같은 값을 쓴다(v0.14 D-241). */
    void changeProfileVisibility(ProfileVisibility visibility) {
        changeItemVisibility(visibility, visibility);
    }

    /** 항목별 공개 여부를 바꾼다. null 항목은 그대로 둔다(v0.14 D-241). */
    void changeItemVisibility(ProfileVisibility ageBand, ProfileVisibility gender) {
        if (ageBand != null) {
            this.ageBandVisibility = ageBand;
        }
        if (gender != null) {
            this.genderVisibility = gender;
        }
        boolean bothPublic =
                ageBandVisibility() == ProfileVisibility.PUBLIC
                        && genderVisibility() == ProfileVisibility.PUBLIC;
        this.profileVisibility = bothPublic ? ProfileVisibility.PUBLIC : ProfileVisibility.PRIVATE;
    }

    /** 나이대 공개 여부. V30 전에 메모리에 올라온 값처럼 비어 있으면 PRIVATE다. */
    ProfileVisibility ageBandVisibility() {
        return ageBandVisibility == null ? ProfileVisibility.PRIVATE : ageBandVisibility;
    }

    /** 성별 공개 여부. 비어 있으면 PRIVATE다. */
    ProfileVisibility genderVisibility() {
        return genderVisibility == null ? ProfileVisibility.PRIVATE : genderVisibility;
    }

    void changeWatchLinked(boolean linked) {
        this.watchLinked = linked;
    }

    void changeRunSettings(RunSettings settings) {
        this.runSettings = settings;
    }

    void changeBattleSettings(BattleSettings settings) {
        this.battleSettings = settings;
    }

    BattleSettings battleSettings() {
        return battleSettings;
    }

    private Set<String> muted() {
        if (mutedNotificationTypes == null || mutedNotificationTypes.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(mutedNotificationTypes.split(",")).collect(Collectors.toSet());
    }
}
