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
     * notification.NotificationType 12개의 이름(자료사전 알림종류, 부록 B). member는 notification에 의존할 수 없어(12.2,
     * notification → member) 이름 목록을 여기 둔다. 두 목록이 같은지는 notification의 시험이 확인한다.
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
                    "BATTLE_CORRECTED");

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 5)
    private DistanceUnit unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_visibility", nullable = false, length = 10)
    private RecordVisibility recordVisibility;

    @Column(name = "course_map_shared", nullable = false)
    private boolean courseMapShared;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_visibility", nullable = false, length = 10)
    private ProfileVisibility profileVisibility;

    /** 꺼진 알림 종류 이름(쉼표 구분). 비면 모두 켜짐. */
    @Column(name = "muted_notification_types", nullable = false, length = 400)
    private String mutedNotificationTypes;

    @Column(name = "watch_linked", nullable = false)
    private boolean watchLinked;

    @Embedded private RunSettings runSettings;

    @Embedded private BattleSettings battleSettings;

    protected MemberSettings() {}

    private MemberSettings(RecordVisibility chosen) {
        this.unit = DistanceUnit.KM;
        this.recordVisibility = chosen;
        this.courseMapShared = false;
        this.profileVisibility = ProfileVisibility.PRIVATE;
        this.mutedNotificationTypes = "";
        this.watchLinked = false;
        this.runSettings = RunSettings.defaults();
        this.battleSettings = BattleSettings.defaults();
    }

    /** 가입 기본값(srs.md 6.3 1.2). 기록 공개 범위는 가입 단계에서 고른 값이다(FR-028). */
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

    /** 모든 항목을 복사한 읽기 전용 사본. 동의와 출생연도는 회원이 넘긴다(GET /me/settings, D-53). */
    MemberSettingsView view(Map<ConsentItem, Boolean> consents, Integer birthYear) {
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
                birthYear);
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

    void changeProfileVisibility(ProfileVisibility visibility) {
        this.profileVisibility = visibility;
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

    private Set<String> muted() {
        if (mutedNotificationTypes == null || mutedNotificationTypes.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(mutedNotificationTypes.split(",")).collect(Collectors.toSet());
    }
}
