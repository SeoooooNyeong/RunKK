package com.a207.runkk.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DistanceUnit;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.Tier;
import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.crew.CrewSummary;
import com.a207.runkk.member.DisclosureLevel;
import com.a207.runkk.member.Gender;
import com.a207.runkk.member.MemberPreference;
import com.a207.runkk.member.MemberProfile;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberStatus;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.member.ProfileVisibility;
import com.a207.runkk.runimal.RunimalQuery;
import com.a207.runkk.runimal.RunimalType;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import com.a207.runkk.running.TotalStats;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 14장 단위: DisclosureLevel.of·showsProfile 3행(v0.14 D-241), ProfileDisclosure.from·filter,
 * ProfileQueryService(D-29, D-52).
 */
@DisplayName("profile 공개 수준과 조립")
class ProfileTest {

    private static final MemberPreference SETTINGS =
            new MemberPreference(
                    DistanceUnit.KM, RecordVisibility.PUBLIC, true, ProfileVisibility.PUBLIC, true);
    private static final RecentRun RECENT =
            new RecentRun(CardKind.FREE_RUN, LocalDate.of(2026, 10, 28), Distance.ofKm(5));

    private static ProfileData data(ProfileVisibility ageBand, ProfileVisibility gender) {
        return new ProfileData(
                5,
                com.a207.runkk.common.DisplayName.of("달림이"),
                "default:RABBIT",
                "KAESONG",
                Tier.GOLD,
                "CHEETAH",
                new ProfileView.Crew(7, "새벽크루", null),
                Distance.ofKm(21),
                SETTINGS,
                new MemberProfile(7, "안녕", "20대", Gender.FEMALE, ageBand, gender),
                Distance.ofKm(120),
                14,
                List.of(RECENT));
    }

    private static final ProfileData DATA =
            data(ProfileVisibility.PUBLIC, ProfileVisibility.PUBLIC);

    @ParameterizedTest(name = "열람자 {0} → 대상 {1}({2}) = {3}, 프로필 보임 {4}")
    @CsvSource({"1,1,ACTIVE,SELF,true", "1,2,WITHDRAWN,WITHDRAWN,false", "1,2,ACTIVE,PUBLIC,true"})
    @DisplayName("DisclosureLevel.of·showsProfile 3행(본인·탈퇴·공개, v0.14 D-241 PRIVATE 삭제)")
    void disclosureLevel(
            long viewer,
            long target,
            MemberStatus status,
            DisclosureLevel expected,
            boolean shows) {
        DisclosureLevel level = DisclosureLevel.of(viewer, target, status);
        assertThat(level).isEqualTo(expected);
        assertThat(level.showsProfile()).isEqualTo(shows);
        assertThat(ProfileDisclosure.from(level).name()).isEqualTo(level.name());
    }

    @Test
    @DisplayName("filter: SELF 전부·공개 값, PUBLIC 설정만 뺌(최장·누적·횟수·소개 늘 보임), WITHDRAWN 표시명만")
    void filterByLevel() {
        ProfileView self = ProfileDisclosure.SELF.filter(DATA);
        assertThat(self.settings()).isEqualTo(SETTINGS);
        assertThat(self.longestDistance()).isEqualTo(Distance.ofKm(21));
        assertThat(self.runimalType()).isEqualTo("CHEETAH");
        assertThat(self.about().level()).isEqualTo(7);
        assertThat(self.about().ageBandVisibility()).isEqualTo(ProfileVisibility.PUBLIC);
        assertThat(self.totalDistance()).isEqualTo(Distance.ofKm(120));
        assertThat(self.runCount()).isEqualTo(14);
        assertThat(self.recentRuns()).as("본인 프로필에는 최근 러닝을 싣지 않는다(D-243)").isEmpty();

        ProfileView open = ProfileDisclosure.PUBLIC.filter(DATA);
        assertThat(open.settings()).isNull();
        assertThat(open.longestDistance())
                .as("최장 거리는 다른 러너에게도 보인다(FR-006)")
                .isEqualTo(Distance.ofKm(21));
        assertThat(open.runimalType()).isEqualTo("CHEETAH");
        assertThat(open.about().intro()).isEqualTo("안녕");
        assertThat(open.about().ageBand()).isEqualTo("20대");
        assertThat(open.about().gender()).isEqualTo(Gender.FEMALE);
        assertThat(open.about().ageBandVisibility()).as("공개 값은 다른 러너에게 싣지 않는다").isNull();
        assertThat(open.totalDistance()).isEqualTo(Distance.ofKm(120));
        assertThat(open.runCount()).isEqualTo(14);
        assertThat(open.recentRuns()).containsExactly(RECENT);
        assertThat(open.avatarBackgroundKey()).isEqualTo("KAESONG");

        ProfileView gone = ProfileDisclosure.WITHDRAWN.filter(DATA);
        assertThat(gone.displayName().value()).isEqualTo("탈퇴한 사용자");
        assertThat(gone.faceKey()).isNull();
        assertThat(gone.avatarBackgroundKey()).isNull();
        assertThat(gone.tier()).isNull();
        assertThat(gone.crew()).isNull();
        assertThat(gone.recentRuns()).isEmpty();
    }

    @ParameterizedTest(name = "나이대 {0}, 성별 {1}")
    @CsvSource({"PUBLIC,PRIVATE", "PRIVATE,PUBLIC", "PRIVATE,PRIVATE"})
    @DisplayName("PUBLIC: 나이대·성별을 항목 공개 값으로 따로 감추고 소개는 늘 보인다(FR-006, D-241)")
    void publicHidesItemsSeparately(ProfileVisibility ageBand, ProfileVisibility gender) {
        ProfileView open = ProfileDisclosure.PUBLIC.filter(data(ageBand, gender));
        assertThat(open.about().ageBand())
                .isEqualTo(ageBand == ProfileVisibility.PUBLIC ? "20대" : null);
        assertThat(open.about().gender())
                .isEqualTo(gender == ProfileVisibility.PUBLIC ? Gender.FEMALE : null);
        assertThat(open.about().intro()).isEqualTo("안녕");
        assertThat(open.about().level()).isEqualTo(7);
    }

    private ProfileQueryService service;
    private MemberQueryService members;
    private AvatarQuery avatars;
    private CrewMembershipReader crews;
    private RunQueryService runs;

    private void wire(RecordVisibility recordVisibility) {
        members = mock(MemberQueryService.class);
        RunimalQuery runimals = mock(RunimalQuery.class);
        avatars = mock(AvatarQuery.class);
        crews = mock(CrewMembershipReader.class);
        runs = mock(RunQueryService.class);
        service =
                new ProfileQueryService(
                        members,
                        runimals,
                        avatars,
                        crews,
                        runs,
                        java.time.Clock.systemDefaultZone());
        when(members.disclosureOf(1, List.of(5L))).thenReturn(Map.of(5L, DisclosureLevel.PUBLIC));
        when(members.disclosureOf(5, List.of(5L))).thenReturn(Map.of(5L, DisclosureLevel.SELF));
        when(members.summaries(List.of(5L)))
                .thenReturn(
                        Map.of(
                                5L,
                                new MemberSummary(
                                        5,
                                        com.a207.runkk.common.DisplayName.of("달림이"),
                                        Tier.SILVER,
                                        ProfileVisibility.PRIVATE)));
        when(members.preferenceOf(5))
                .thenReturn(
                        new MemberPreference(
                                DistanceUnit.KM,
                                recordVisibility,
                                false,
                                ProfileVisibility.PRIVATE,
                                false));
        when(members.profileOf(
                        org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(
                        new MemberProfile(
                                3,
                                "소개",
                                "30대",
                                Gender.MALE,
                                ProfileVisibility.PRIVATE,
                                ProfileVisibility.PUBLIC));
        when(runimals.typesOf(List.of(5L))).thenReturn(Map.of(5L, RunimalType.values()[0]));
        when(avatars.faceKey(5)).thenReturn(Optional.of("default:BEAR"));
        when(avatars.backgroundKeyOf(5)).thenReturn(Optional.of("INCHEON"));
        when(crews.activeCrewOf(5)).thenReturn(Optional.of(new CrewSummary(7, "새벽크루", "logo")));
        when(runs.longestDistance(5)).thenReturn(Optional.of(Distance.ofKm(10)));
        when(runs.totals(5)).thenReturn(new TotalStats(Distance.ofKm(42), Duration.ofHours(4), 9));
        when(runs.recentSummaries(5, ProfileQueryService.RECENT_RUNS))
                .thenReturn(
                        List.of(
                                new RunSummary(
                                        "r1",
                                        5,
                                        LocalDateTime.of(2026, 10, 27, 7, 0),
                                        Distance.ofKm(5),
                                        Duration.ofMinutes(30),
                                        new com.a207.runkk.common.Pace(360),
                                        true,
                                        50.0,
                                        RunMode.BATTLE,
                                        CardKind.BATTLE_RUN,
                                        11L,
                                        null)));
    }

    @Test
    @DisplayName("ProfileQueryService: 다른 러너는 누적 거리·횟수·최근 러닝 3건, 나이대·성별은 항목 공개 값대로(D-241·D-243)")
    void queryService() {
        wire(RecordVisibility.PUBLIC);

        ProfileView view = service.view(1, 5);
        assertThat(view.disclosure()).isEqualTo(ProfileDisclosure.PUBLIC);
        assertThat(view.faceKey()).isEqualTo("default:BEAR");
        assertThat(view.avatarBackgroundKey()).isEqualTo("INCHEON");
        assertThat(view.crew().logoUrl()).isEqualTo("logo");
        assertThat(view.runimalType()).isEqualTo(RunimalType.values()[0].name());
        assertThat(view.longestDistance()).isEqualTo(Distance.ofKm(10));
        assertThat(view.totalDistance()).isEqualTo(Distance.ofKm(42));
        assertThat(view.runCount()).isEqualTo(9);
        assertThat(view.about().ageBand()).isNull();
        assertThat(view.about().gender()).isEqualTo(Gender.MALE);
        assertThat(view.recentRuns())
                .containsExactly(
                        new RecentRun(
                                CardKind.BATTLE_RUN, LocalDate.of(2026, 10, 27), Distance.ofKm(5)));
        assertThat(view.recentRuns().get(0).kindName()).isEqualTo("BATTLE_RUN");

        ProfileView mine = service.view(5, 5);
        assertThat(mine.recentRuns()).isEmpty();
        assertThat(mine.about().ageBand()).isEqualTo("30대");
        assertThat(mine.about().ageBandVisibility()).isEqualTo(ProfileVisibility.PRIVATE);

        when(members.disclosureOf(1, List.of(6L)))
                .thenReturn(Map.of(6L, DisclosureLevel.WITHDRAWN));
        assertThat(service.view(1, 6).displayName().value()).isEqualTo("탈퇴한 사용자");
        verify(avatars, never()).faceKey(6);

        when(members.disclosureOf(any(Long.class), any())).thenReturn(Map.of());
        assertThatThrownBy(() -> service.view(1, 99)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "기록 공개 {0}, 같은 크루 {1} → 최근 러닝 보임 {2}")
    @CsvSource({"PUBLIC,false,true", "CREW,true,true", "CREW,false,false", "PRIVATE,true,false"})
    @DisplayName("최근 러닝 3건은 대상의 기록 공개 범위를 통과할 때만 싣는다(FR-060, D-243)")
    void recentRunsFollowRecordVisibility(
            RecordVisibility visibility, boolean sameCrew, boolean shown) {
        wire(visibility);
        when(crews.sameCrew(1, 5)).thenReturn(sameCrew);
        assertThat(service.view(1, 5).recentRuns().isEmpty()).isEqualTo(!shown);
        assertThat(service.view(1, 5).runCount()).as("누적 값은 늘 보인다").isEqualTo(9);
    }
}
