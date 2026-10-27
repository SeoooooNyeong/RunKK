package com.a207.runkk.report.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.crew.CrewPostService;
import com.a207.runkk.crew.PostKind;
import com.a207.runkk.crew.ReportedPostView;
import com.a207.runkk.crew.chat.CrewChatService;
import com.a207.runkk.crew.chat.ReportedChatView;
import com.a207.runkk.report.ReportTargetKind;
import com.a207.runkk.report.ReportedContent;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 설계 06-04 CrewPostReportAdapter·ChatReportAdapter(report.target, D-115, D-135, H-31). TC-6134,
 * TC-6135, TC-6864.
 */
@DisplayName("report.target 신고 대상 연결")
class ReportAdaptersTest {

    private static final long CREW = 7;

    private final CrewPostService posts = mock(CrewPostService.class);
    private final CrewChatService chats = mock(CrewChatService.class);
    private final CrewMembershipReader membership = mock(CrewMembershipReader.class);
    private final CrewPostReportAdapter postAdapter = new CrewPostReportAdapter(posts, membership);
    private final ChatReportAdapter chatAdapter = new ChatReportAdapter(chats, membership);

    @BeforeEach
    void setUp() {
        when(membership.activeMemberIds(CREW)).thenReturn(List.of(1L, 3L, 4L));
    }

    private void post(long id, PostKind kind, boolean hidden) {
        when(posts.reportedView(id))
                .thenReturn(
                        Optional.of(new ReportedPostView(id, CREW, kind, 3L, "본문" + id, hidden)));
    }

    @Test
    @DisplayName("TC-6134: 게시글 연결은 CHEER·SCHEDULE·NOTICE·POLL·BOARD를, 채팅 연결은 CHAT_MESSAGE만 맡는다")
    void supports() {
        assertThat(EnumSet.allOf(ReportTargetKind.class))
                .filteredOn(postAdapter::supports)
                .containsExactly(
                        ReportTargetKind.CHEER,
                        ReportTargetKind.SCHEDULE,
                        ReportTargetKind.NOTICE,
                        ReportTargetKind.POLL,
                        ReportTargetKind.BOARD);
        assertThat(EnumSet.allOf(ReportTargetKind.class))
                .filteredOn(chatAdapter::supports)
                .containsExactly(ReportTargetKind.CHAT_MESSAGE);
    }

    @ParameterizedTest
    @EnumSource(PostKind.class)
    @DisplayName("TC-6864: 모든 PostKind가 신고 대상 종류 하나에 대응한다(CREW_RUN·MEETUP → SCHEDULE)")
    void everyPostKindMapsToATargetKind(PostKind kind) {
        ReportTargetKind target = CrewPostReportAdapter.targetKindOf(kind);
        assertThat(target).isNotEqualTo(ReportTargetKind.CHAT_MESSAGE);
        assertThat(target.name())
                .isEqualTo(
                        kind == PostKind.CREW_RUN || kind == PostKind.MEETUP
                                ? "SCHEDULE"
                                : kind.name());
    }

    @Test
    @DisplayName("TC-6134·TC-6606: 크루런·대면 모임은 SCHEDULE로 찾고, 종류 불일치·삭제는 빈 값, 숨긴 글은 hidden 참으로 돌려준다")
    void postFind() {
        post(601, PostKind.MEETUP, false);
        post(602, PostKind.CREW_RUN, false);
        post(603, PostKind.POLL, false);
        post(605, PostKind.NOTICE, true);
        when(posts.reportedView(604)).thenReturn(Optional.empty());

        ReportedContent meetup = postAdapter.find(ReportTargetKind.SCHEDULE, 601).orElseThrow();
        assertThat(meetup.crewId()).isEqualTo(CREW);
        assertThat(meetup.authorId()).isEqualTo(3L);
        assertThat(meetup.preview()).isEqualTo("본문601");
        assertThat(meetup.hidden()).isFalse();
        assertThat(meetup.isCrewMember(3)).isTrue();
        assertThat(meetup.isCrewMember(9)).isFalse();
        assertThat(postAdapter.find(ReportTargetKind.SCHEDULE, 602)).isPresent();
        assertThat(postAdapter.find(ReportTargetKind.CHEER, 603)).isEmpty();
        assertThat(postAdapter.find(ReportTargetKind.BOARD, 604)).isEmpty();
        assertThat(postAdapter.find(ReportTargetKind.NOTICE, 605).orElseThrow().hidden()).isTrue();
        assertThat(postAdapter.find(ReportTargetKind.CHAT_MESSAGE, 601)).isEmpty();
    }

    @Test
    @DisplayName("TC-6134: 게시글 hide·unhide는 CrewPostService.hideReported·unhideReported만 부른다")
    void postHideUnhide() {
        postAdapter.hide(ReportTargetKind.BOARD, 801);
        postAdapter.unhide(ReportTargetKind.BOARD, 801);
        verify(posts).hideReported(801);
        verify(posts).unhideReported(801);
        verifyNoInteractions(chats);
    }

    @Test
    @DisplayName("TC-6135: 채팅은 숨긴 메시지도 hidden 참으로, 삭제된 메시지는 빈 값, 다른 종류는 묻지 않는다")
    void chatFind() {
        when(chats.reportedView(701))
                .thenReturn(Optional.of(new ReportedChatView(701, CREW, 4L, "안녕", true)));
        when(chats.reportedView(702)).thenReturn(Optional.empty());
        ReportedContent hidden = chatAdapter.find(ReportTargetKind.CHAT_MESSAGE, 701).orElseThrow();
        assertThat(hidden.hidden()).isTrue();
        assertThat(hidden.authorId()).isEqualTo(4L);
        assertThat(hidden.isCrewMember(1)).isTrue();
        assertThat(chatAdapter.find(ReportTargetKind.CHAT_MESSAGE, 702)).isEmpty();
        assertThat(chatAdapter.find(ReportTargetKind.CHEER, 701)).isEmpty();
        verify(chats, never()).reportedView(0);
    }

    @Test
    @DisplayName("채팅 hide·unhide는 CrewChatService.hideReported·unhideReported만 부른다")
    void chatHideUnhide() {
        chatAdapter.hide(ReportTargetKind.CHAT_MESSAGE, 701);
        chatAdapter.unhide(ReportTargetKind.CHAT_MESSAGE, 701);
        verify(chats).hideReported(701);
        verify(chats).unhideReported(701);
        verifyNoInteractions(posts);
    }
}
