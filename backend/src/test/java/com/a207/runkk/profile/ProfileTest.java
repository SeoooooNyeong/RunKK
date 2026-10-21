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
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
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
import com.a207.runkk.running.RunQueryService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 14장 단위: DisclosureLevel.of·showsProfile 4행, ProfileDisclosure.from·filter,
 * ProfileQueryService(D-29, D-52).
 */
@DisplayName("profile 공개 수준과 조립")
class ProfileTest {

    private static final MemberPreference SETTINGS =
            new MemberPreference(
                    DistanceUnit.KM, RecordVisibility.PUBLIC, true, ProfileVisibility.PUBLIC, true);
    private static final ProfileData DATA =
            new ProfileData(
                    5,
                    com.a207.runkk.common.DisplayName.of("달림이"),
                    "default:RABBIT",
                    Tier.GOLD,
                    Set.of(PersonalityTag.SOLO_RUN),
                    "CHEETAH",
                    new ProfileView.Crew(7, "새벽크루", null),
                    Distance.ofKm(21),
                    SETTINGS,
                    new MemberProfile(7, "photo", "안녕", "20대", Gender.FEMALE));

    @ParameterizedTest(name = "열람자 {0} → 대상 {1}({2}, {3}) = {4}, 프로필 보임 {5}")
    @CsvSource({
        "1,1,ACTIVE,PRIVATE,SELF,true",
        "1,2,WITHDRAWN,PUBLIC,WITHDRAWN,false",
        "1,2,ACTIVE,PUBLIC,PUBLIC,true",
        "1,2,ACTIVE,PRIVATE,PRIVATE,false"
    })
    @DisplayName("DisclosureLevel.of·showsProfile 4행(본인·탈퇴·공개·비공개)")
    void disclosureLevel(
            long viewer,
            long target,
            MemberStatus status,
            ProfileVisibility visibility,
            DisclosureLevel expected,
            boolean shows) {
        DisclosureLevel level = DisclosureLevel.of(viewer, target, status, visibility);
        assertThat(level).isEqualTo(expected);
        assertThat(level.showsProfile()).isEqualTo(shows);
        assertThat(ProfileDisclosure.from(level).name()).isEqualTo(level.name());
    }

    @Test
    @DisplayName("filter: SELF 전부, PUBLIC 설정·최장 거리 뺌, PRIVATE 표시명·얼굴·티어·크루만, WITHDRAWN 표시명만")
    void filterByLevel() {
        ProfileView self = ProfileDisclosure.SELF.filter(DATA);
        assertThat(self.settings()).isEqualTo(SETTINGS);
        assertThat(self.longestDistance()).isEqualTo(Distance.ofKm(21));
        assertThat(self.runimalType()).isEqualTo("CHEETAH");
        assertThat(self.about().level()).isEqualTo(7);

        ProfileView open = ProfileDisclosure.PUBLIC.filter(DATA);
        assertThat(open.settings()).isNull();
        assertThat(open.longestDistance()).isNull();
        assertThat(open.tags()).containsExactly(PersonalityTag.SOLO_RUN);
        assertThat(open.runimalType()).isEqualTo("CHEETAH");
        assertThat(open.about().intro()).isEqualTo("안녕");

        ProfileView hidden = ProfileDisclosure.PRIVATE.filter(DATA);
        assertThat(hidden.displayName().value()).isEqualTo("달림이");
        assertThat(hidden.faceKey()).isEqualTo("default:RABBIT");
        assertThat(hidden.tier()).isEqualTo(Tier.GOLD);
        assertThat(hidden.crew().name()).isEqualTo("새벽크루");
        assertThat(hidden.tags()).isEmpty();
        assertThat(hidden.runimalType()).isNull();
        assertThat(hidden.settings()).isNull();
        assertThat(hidden.about()).as("나이대·성별·소개 감춤(FR-006)").isNull();

        ProfileView gone = ProfileDisclosure.WITHDRAWN.filter(DATA);
        assertThat(gone.displayName().value()).isEqualTo("탈퇴한 사용자");
        assertThat(gone.faceKey()).isNull();
        assertThat(gone.tier()).isNull();
        assertThat(gone.crew()).isNull();
    }

    @Test
    @DisplayName("ProfileQueryService: member가 정한 수준으로 거르고 탈퇴 회원은 다른 패키지를 부르지 않는다")
    void queryService() {
        MemberQueryService members = mock(MemberQueryService.class);
        RunimalQuery runimals = mock(RunimalQuery.class);
        AvatarQuery avatars = mock(AvatarQuery.class);
        CrewMembershipReader crews = mock(CrewMembershipReader.class);
        RunQueryService runs = mock(RunQueryService.class);
        ProfileQueryService service =
                new ProfileQueryService(
                        members,
                        runimals,
                        avatars,
                        crews,
                        runs,
                        java.time.Clock.systemDefaultZone());
        when(members.disclosureOf(1, List.of(5L))).thenReturn(Map.of(5L, DisclosureLevel.PRIVATE));
        when(members.summaries(List.of(5L)))
                .thenReturn(
                        Map.of(
                                5L,
                                new MemberSummary(
                                        5,
                                        com.a207.runkk.common.DisplayName.of("달림이"),
                                        Tier.SILVER,
                                        ProfileVisibility.PRIVATE,
                                        TagSet.of(
                                                Set.of(PersonalityTag.SOLO_RUN),
                                                TagOwner.RUNNER))));
        when(members.preferenceOf(5)).thenReturn(SETTINGS);
        when(members.profileOf(
                        org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new MemberProfile(3, null, null, null, null));
        when(runimals.typesOf(List.of(5L))).thenReturn(Map.of(5L, RunimalType.values()[0]));
        when(avatars.faceKey(5)).thenReturn(Optional.of("default:BEAR"));
        when(crews.activeCrewOf(5)).thenReturn(Optional.of(new CrewSummary(7, "새벽크루", "logo")));
        when(runs.longestDistance(5)).thenReturn(Optional.of(Distance.ofKm(10)));

        ProfileView view = service.view(1, 5);
        assertThat(view.disclosure()).isEqualTo(ProfileDisclosure.PRIVATE);
        assertThat(view.faceKey()).isEqualTo("default:BEAR");
        assertThat(view.crew().logoUrl()).isEqualTo("logo");
        assertThat(view.runimalType()).isNull();
        assertThat(view.longestDistance()).isNull();

        when(members.disclosureOf(1, List.of(6L)))
                .thenReturn(Map.of(6L, DisclosureLevel.WITHDRAWN));
        assertThat(service.view(1, 6).displayName().value()).isEqualTo("탈퇴한 사용자");
        verify(avatars, never()).faceKey(6);

        when(members.disclosureOf(any(Long.class), any())).thenReturn(Map.of());
        assertThatThrownBy(() -> service.view(1, 99)).isInstanceOf(IllegalArgumentException.class);
    }
}
