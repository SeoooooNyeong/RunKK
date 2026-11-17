package com.a207.runkk.web.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.crew.CrewMemberProfile;
import com.a207.runkk.crew.CrewMembersView;
import com.a207.runkk.crew.CrewQueryService;
import com.a207.runkk.crew.CrewRole;
import com.a207.runkk.runimal.RunimalQuery;
import com.a207.runkk.web.crew.CrewMembersAssembler.CrewMembersResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 크루원 목록의 얼굴 키와 표시 성별(설계 5.31 A, [임시:D-성-226]·[임시:D-성-227]). 얼굴 키는 disclosedMemberIds(=
 * DisclosureLevel.showsProfile())에 든 크루원만 읽는다.
 */
@org.junit.jupiter.api.DisplayName("web.crew CrewMembersAssembler 얼굴 키·표시 성별")
class CrewMembersAssemblerTest {

    private static final long CREW = 7;
    private static final long VIEWER = 1;

    private final CrewQueryService crewQueryService = mock(CrewQueryService.class);
    private final RunimalQuery runimalQuery = mock(RunimalQuery.class);
    private final AvatarQuery avatarQuery = mock(AvatarQuery.class);
    private final CrewMembersAssembler assembler =
            new CrewMembersAssembler(crewQueryService, runimalQuery, avatarQuery);

    private static CrewMembersView.Line line(long memberId, String shownGender) {
        return new CrewMembersView.Line(
                memberId,
                DisplayName.of("러너" + memberId),
                CrewRole.MEMBER,
                null,
                null,
                Distance.ZERO,
                LocalDateTime.of(2026, 11, 1, 9, 0),
                CrewMemberProfile.shownTo(null, memberId == VIEWER),
                shownGender);
    }

    private CrewMembersResponse.Member memberOf(long memberId) {
        return assembler.members(VIEWER, CREW).members().stream()
                .filter(m -> m.memberId() == memberId)
                .findFirst()
                .orElseThrow();
    }

    private void view(Set<Long> disclosed, CrewMembersView.Line... lines) {
        when(crewQueryService.members(VIEWER, CREW))
                .thenReturn(new CrewMembersView(CREW, List.of(lines), disclosed));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("크루원 목록 행은 공개 크루원의 faceKey를 싣는다")
    void 크루원_목록_행은_공개_크루원의_faceKey를_싣는다() {
        view(Set.of(1L, 2L), line(1, "MALE"), line(2, "FEMALE"));
        when(avatarQuery.faceKeysOf(Set.of(1L, 2L)))
                .thenReturn(Map.of(1L, "default:CAT", 2L, "head:2:ab12"));

        assertThat(memberOf(1).faceKey()).isEqualTo("default:CAT");
        assertThat(memberOf(2).faceKey()).isEqualTo("head:2:ab12");
        assertThat(memberOf(2).shownGender()).isEqualTo("FEMALE");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("탈퇴 상태(프로필을 보이지 않는 크루원)는 faceKey가 null이다: 얼굴 키를 묻지도 않는다")
    void 탈퇴_상태는_faceKey가_null이다() {
        view(Set.of(1L), line(1, "MALE"), line(3, "FEMALE"));
        when(avatarQuery.faceKeysOf(anyCollection()))
                .thenReturn(Map.of(1L, "default:CAT", 3L, "head:3:zz99"));

        assertThat(memberOf(3).faceKey()).isNull();
        assertThat(memberOf(1).faceKey()).isEqualTo("default:CAT");
        verify(avatarQuery, atLeastOnce()).faceKeysOf(Set.of(1L));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "성별 비공개는 표시 성별 대체값이다: 감춘 gender는 null이고 shownGender는 늘 MALE·FEMALE")
    void 성별_비공개는_표시성별_대체값이다() {
        view(Set.of(1L, 2L), line(1, "MALE"), line(2, "FEMALE"));

        CrewMembersResponse.Member hidden = memberOf(2);

        assertThat(hidden.gender()).isNull();
        assertThat(hidden.shownGender()).isEqualTo("FEMALE");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("얼굴을 안 고른 크루원은 faceKey가 null이다")
    void 얼굴을_안_고른_크루원은_faceKey가_null이다() {
        view(Set.of(1L, 2L), line(1, "MALE"), line(2, "FEMALE"));
        when(avatarQuery.faceKeysOf(Set.of(1L, 2L))).thenReturn(Map.of(1L, "default:CAT"));

        assertThat(memberOf(2).faceKey()).isNull();
        assertThat(memberOf(2).shownGender()).isEqualTo("FEMALE");
    }
}
