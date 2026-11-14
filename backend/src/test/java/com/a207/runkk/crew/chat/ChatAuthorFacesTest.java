package com.a207.runkk.crew.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.member.DisclosureLevel;
import com.a207.runkk.member.Gender;
import com.a207.runkk.member.MemberQueryService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 채팅 작성자·반응자 얼굴(설계 5.31 A, [임시:D-성-230]). 공개 판정은 DisclosureLevel.showsProfile() 한 곳이다. */
@DisplayName("crew.chat ChatAuthorFaces")
class ChatAuthorFacesTest {

    private final MemberQueryService memberQuery = mock(MemberQueryService.class);
    private final AvatarQuery avatarQuery = mock(AvatarQuery.class);
    private final ChatAuthorFaces faces = new ChatAuthorFaces(memberQuery, avatarQuery);

    @Test
    @DisplayName("탈퇴 작성자는 둘 다 null: 작성자 없음(null)과 탈퇴 상태 회원은 맵에 없다")
    void 탈퇴_작성자는_둘_다_null() {
        when(memberQuery.disclosureOf(1L, Set.of(5L)))
                .thenReturn(Map.of(5L, DisclosureLevel.WITHDRAWN));

        assertThat(faces.of(1, Arrays.asList(null, 5L))).isEmpty();
        assertThat(faces.of(1, Arrays.asList((Long) null))).isEmpty();
        assertThat(faces.of(1, List.of())).isEmpty();
        verifyNoInteractions(avatarQuery);
        verify(memberQuery, never()).shownGendersOf(anyCollection());
    }

    @Test
    @DisplayName("공개 작성자는 faceKey와 표시 성별: 얼굴을 안 골랐으면 faceKey만 null")
    void 공개_작성자는_faceKey와_표시성별() {
        when(memberQuery.disclosureOf(1L, Set.of(1L, 2L, 3L)))
                .thenReturn(
                        Map.of(
                                1L, DisclosureLevel.SELF,
                                2L, DisclosureLevel.PUBLIC,
                                3L, DisclosureLevel.WITHDRAWN));
        when(avatarQuery.faceKeysOf(Set.of(1L, 2L))).thenReturn(Map.of(2L, "head:2:ab12"));
        when(memberQuery.shownGendersOf(Set.of(1L, 2L)))
                .thenReturn(Map.of(1L, Gender.MALE, 2L, Gender.FEMALE));

        Map<Long, ChatAuthorFaces.Face> result = faces.of(1, List.of(1L, 2L, 3L, 2L));

        assertThat(result)
                .containsOnly(
                        Map.entry(1L, new ChatAuthorFaces.Face(null, "MALE")),
                        Map.entry(2L, new ChatAuthorFaces.Face("head:2:ab12", "FEMALE")));
    }

    @Test
    @DisplayName("한 번의 묶음 조회로 읽는다: 사람 수와 상관없이 공개 판정·얼굴 키·표시 성별을 한 번씩")
    void 한_번의_묶음_조회로_읽는다() {
        Set<Long> ids = Set.of(1L, 2L, 3L, 4L);
        when(memberQuery.disclosureOf(1L, ids))
                .thenReturn(
                        Map.of(
                                1L, DisclosureLevel.SELF,
                                2L, DisclosureLevel.PUBLIC,
                                3L, DisclosureLevel.PUBLIC,
                                4L, DisclosureLevel.PUBLIC));

        faces.of(1, List.of(1L, 2L, 3L, 4L, 2L, 3L));

        verify(memberQuery, times(1)).disclosureOf(anyLong(), anyCollection());
        verify(avatarQuery, times(1)).faceKeysOf(anyCollection());
        verify(memberQuery, times(1)).shownGendersOf(anyCollection());
    }
}
