package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 2.1.10.8~2.1.10.10 게시 계층(D-99): SchedulePost.answer 다형성(MeetupPost·CrewRunPost), PollPost,
 * NoticePost, BoardPost, CrewPost 공통 규칙. N = 2026-10-22T19:00, 크루 7, 1 LEADER, 2 MANAGER, 3·4
 * MEMBER, 9 크루 밖.
 */
@DisplayName("크루 게시 계층(TC-6074~TC-6103)")
class CrewPostHierarchyTest {

    private static final LocalDateTime N = LocalDateTime.of(2026, 10, 22, 19, 0);
    private static final LocalDateTime MEET = LocalDateTime.of(2026, 10, 25, 10, 0);
    private static final long CREW = 7;

    private static ReasonCode reasonOf(Throwable e) {
        return ((BusinessRuleException) e).reason();
    }

    private static MeetupPost meetup(long author, Integer capacity) {
        return new MeetupPost(CREW, author, new MeetupDraft("", MEET, "한강공원", capacity), N);
    }

    @Nested
    @DisplayName("MeetupPost.answer(정원 규칙)")
    class Meetup {

        @Test
        @DisplayName("TC-6074: 삭제된 글은 상태 위반")
        void deleted() {
            MeetupPost post = meetup(1, 10);
            post.delete(1, CrewRelation.LEADER);
            assertThatThrownBy(() -> post.answer(3, AttendanceAnswer.GOING, N))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("TC-6075: 일정일시 전까지만, 정각부터 VOTE_CLOSED")
        void closesAtSchedule() {
            MeetupPost post = meetup(1, 10);
            assertThat(post.answer(3, AttendanceAnswer.GOING, MEET.minusSeconds(1)).goingCount())
                    .isEqualTo(2);
            for (LocalDateTime at : List.of(MEET, MEET.plusSeconds(1))) {
                assertThatThrownBy(() -> post.answer(4, AttendanceAnswer.GOING, at))
                        .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.VOTE_CLOSED));
            }
        }

        @Test
        @DisplayName("TC-6076·TC-6077: 정원 2에 GOING 2면 SCHEDULE_FULL, 1이면 받는다")
        void capacity() {
            MeetupPost full = meetup(1, 2);
            full.answer(4, AttendanceAnswer.GOING, N);
            assertThatThrownBy(() -> full.answer(3, AttendanceAnswer.GOING, N))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.SCHEDULE_FULL));
            assertThat(full.attendanceOf(3)).isEmpty();

            MeetupPost room = meetup(1, 2);
            assertThat(room.answer(3, AttendanceAnswer.GOING, N))
                    .isEqualTo(new AttendanceView(0, AttendanceAnswer.GOING, 2, 0, 0));
        }

        @Test
        @DisplayName("TC-6078: 참석이 아닌 응답은 정원과 무관")
        void nonGoingIgnoresCapacity() {
            MeetupPost post = meetup(1, 2);
            post.answer(2, AttendanceAnswer.GOING, N);
            post.answer(3, AttendanceAnswer.UNDECIDED, N);
            post.answer(4, AttendanceAnswer.NOT_GOING, N);
            assertThat(post.counts()).isEqualTo(new AttendanceCounts(2, 1, 1));
        }

        @Test
        @DisplayName("TC-6079·TC-6080: 응답 변경은 행 하나, 이미 GOING인 회원의 GOING은 정원이 차도 받는다, 빠지면 자리가 생긴다")
        void changeAnswer() {
            MeetupPost post = meetup(1, 2);
            post.answer(3, AttendanceAnswer.NOT_GOING, N);
            post.answer(3, AttendanceAnswer.GOING, N);
            assertThat(post.attendances()).hasSize(2);
            assertThat(post.counts()).isEqualTo(new AttendanceCounts(2, 0, 0));

            MeetupPost full = meetup(1, 2);
            full.answer(4, AttendanceAnswer.GOING, N);
            full.answer(1, AttendanceAnswer.GOING, N);
            assertThat(full.counts().going()).isEqualTo(2);
            full.answer(1, AttendanceAnswer.NOT_GOING, N);
            full.answer(3, AttendanceAnswer.GOING, N);
            assertThat(full.counts()).isEqualTo(new AttendanceCounts(2, 1, 0));
        }

        @Test
        @DisplayName(
                "TC-6081: 장소명·정원 범위는 400(IllegalArgumentException), 일시는 SCHEDULE_INVALID, 본문 201자"
                        + " BODY_TOO_LONG")
        void draftBoundaries() {
            for (String place : List.of("", "가".repeat(51))) {
                assertThatThrownBy(() -> new MeetupDraft("", MEET, place, null))
                        .isInstanceOf(IllegalArgumentException.class);
            }
            assertThat(new MeetupDraft("", MEET, "가", null).placeName()).isEqualTo("가");
            assertThat(new MeetupDraft("", MEET, "가".repeat(50), null)).isNotNull();
            for (int cap : new int[] {1, 11}) {
                assertThatThrownBy(() -> new MeetupDraft("", MEET, "공원", cap))
                        .isInstanceOf(IllegalArgumentException.class);
            }
            assertThat(meetup(1, 2).capacity()).isEqualTo(2);
            assertThat(meetup(1, 10).capacity()).isEqualTo(10);
            assertThat(meetup(1, null).capacity()).isEqualTo(Crew.CAPACITY);
            assertThatThrownBy(() -> new MeetupPost(CREW, 1, new MeetupDraft("", N, "공원", null), N))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.SCHEDULE_INVALID));
            assertThat(
                            new MeetupPost(
                                    CREW, 1, new MeetupDraft("", N.plusSeconds(1), "공원", null), N))
                    .isNotNull();
            assertThat(
                            new MeetupPost(
                                    CREW, 1, new MeetupDraft("가".repeat(200), MEET, "공원", null), N))
                    .isNotNull();
            assertThatThrownBy(
                            () ->
                                    new MeetupPost(
                                            CREW,
                                            1,
                                            new MeetupDraft("가".repeat(201), MEET, "공원", null),
                                            N))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.BODY_TOO_LONG));
        }

        @Test
        @DisplayName("TC-6082: 작성자는 GOING으로 들어가 정원에 들고, 뒤에 응답을 바꿀 수 있다(D-151)")
        void authorGoes() {
            MeetupPost post = meetup(3, 2);
            assertThat(post.attendanceOf(3).orElseThrow().answer())
                    .isEqualTo(AttendanceAnswer.GOING);
            assertThat(post.counts()).isEqualTo(new AttendanceCounts(1, 0, 0));
            post.answer(3, AttendanceAnswer.NOT_GOING, N);
            assertThat(post.counts()).isEqualTo(new AttendanceCounts(0, 1, 0));
            assertThat(post.summary(3).meetup().myAnswer()).isEqualTo(AttendanceAnswer.NOT_GOING);
            assertThat(post.summary(3).meetup().placeName()).isEqualTo("한강공원");
        }
    }

    @Test
    @DisplayName(
            "TC-6083: 크루런은 작성자 GOING, UNDECIDED는 참가자가 아님, 마감 정각부터 VOTE_CLOSED, 대기실 입장은 응답이 무엇이든"
                    + " GOING")
    void crewRunAnswerAndLobby() {
        LocalDateTime deadline = LocalDateTime.of(2026, 10, 25, 9, 30);
        CrewRunPost post =
                new CrewRunPost(
                        CREW, 3, new CrewRunDraft(null, MEET, StandardDistance.KM5, deadline), N);
        assertThat(post.counts()).isEqualTo(new AttendanceCounts(1, 0, 0));
        post.answer(4, AttendanceAnswer.GOING, N);
        assertThat(post.participantsToRemind()).contains(4L);
        post.answer(4, AttendanceAnswer.UNDECIDED, N);
        assertThat(post.participantsToRemind()).doesNotContain(4L);
        assertThat(post.counts()).isEqualTo(new AttendanceCounts(1, 0, 1));

        post.answer(1, AttendanceAnswer.UNDECIDED, deadline.minusSeconds(1));
        post.answer(2, AttendanceAnswer.NOT_GOING, N);
        for (LocalDateTime at : List.of(deadline, deadline.plusSeconds(1))) {
            assertThatThrownBy(() -> post.answer(1, AttendanceAnswer.GOING, at))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.VOTE_CLOSED));
        }
        LocalDateTime inLobby = MEET.minusMinutes(20);
        for (long member : new long[] {1, 2, 6}) {
            post.enterLobby(member, inLobby, CrewFixtures.RULE);
            assertThat(post.participationOf(member).orElseThrow().answer())
                    .isEqualTo(AttendanceAnswer.GOING);
            assertThat(post.readinessOf(member)).contains(Readiness.ENTERING);
        }
    }

    @Nested
    @DisplayName("PollPost")
    class Poll {

        private final LocalDateTime closes = LocalDateTime.of(2026, 10, 24, 21, 0);

        private PollPost yesNo() {
            return new PollPost(
                    CREW, 1, new PollDraft("토요일 모일까요?", PollMethod.YES_NO, List.of(), closes), N);
        }

        private PollPost choices(int count) {
            List<String> options = List.of("가", "나", "다", "라", "마").subList(0, count);
            return new PollPost(
                    CREW, 1, new PollDraft("어디서?", PollMethod.CHOICES, options, closes), N);
        }

        @Test
        @DisplayName("TC-6085: 마감 1초 전은 받고 정각·뒤는 POLL_CLOSED(isClosed 정각 포함)")
        void closesAtExactTime() {
            PollPost poll = yesNo();
            poll.respond(3, 1, closes.minusSeconds(1));
            assertThat(poll.isClosed(closes.minusSeconds(1))).isFalse();
            for (LocalDateTime at : List.of(closes, closes.plusSeconds(1))) {
                assertThat(poll.isClosed(at)).isTrue();
                assertThatThrownBy(() -> poll.respond(4, 1, at))
                        .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.POLL_CLOSED));
            }
        }

        @Test
        @DisplayName("TC-6086: 없는 선택지는 POLL_INVALID")
        void unknownOption() {
            for (int no : new int[] {0, 3}) {
                assertThatThrownBy(() -> yesNo().respond(3, no, N))
                        .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.POLL_INVALID));
            }
            assertThatThrownBy(() -> choices(3).respond(3, 4, N))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.POLL_INVALID));
        }

        @Test
        @DisplayName("TC-6087: 회원당 한 응답, 다시 고르면 바뀐다")
        void changeResponse() {
            PollPost poll = choices(3);
            poll.respond(3, 1, N);
            poll.respond(3, 2, N);
            assertThat(poll.responses())
                    .singleElement()
                    .satisfies(r -> assertThat(r.optionNo()).isEqualTo(2));
            assertThat(poll.summary(3).poll().myOptionNo()).isEqualTo(2);
            assertThat(poll.summary(3).poll().responseCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("TC-6088: 찬반은 \"찬성\"·\"반대\"를 만들고 선택지를 보내면 400")
        void yesNoOptions() {
            assertThat(yesNo().options())
                    .containsExactly(new PollOption(1, "찬성"), new PollOption(2, "반대"));
            assertThatThrownBy(
                            () -> new PollDraft("질문", PollMethod.YES_NO, List.of("가", "나"), closes))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("TC-6089·TC-6090: 선택지 2~5개·1~40자, 질문 1~200자는 400, 마감이 지금 이전이면 POLL_INVALID")
        void draftBoundaries() {
            for (List<String> bad :
                    List.of(
                            List.of("가"),
                            Collections.nCopies(6, "가"),
                            List.of("", "나"),
                            List.of("가".repeat(41), "나"))) {
                assertThatThrownBy(() -> new PollDraft("질문", PollMethod.CHOICES, bad, closes))
                        .isInstanceOf(IllegalArgumentException.class);
            }
            assertThat(choices(2).options()).hasSize(2);
            assertThat(choices(5).options()).hasSize(5);
            assertThat(
                            new PollDraft(
                                    "질문", PollMethod.CHOICES, List.of("가", "나".repeat(40)), closes))
                    .isNotNull();
            assertThatThrownBy(
                            () ->
                                    new PollDraft(
                                            "가".repeat(201), PollMethod.YES_NO, List.of(), closes))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(
                            new PollPost(
                                    CREW,
                                    1,
                                    new PollDraft("가".repeat(200), PollMethod.YES_NO, null, closes),
                                    N))
                    .isNotNull();
            assertThatThrownBy(
                            () ->
                                    new PollPost(
                                            CREW,
                                            1,
                                            new PollDraft("질문", PollMethod.YES_NO, null, N),
                                            N))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.POLL_INVALID));
            assertThat(
                            new PollPost(
                                    CREW,
                                    1,
                                    new PollDraft("질문", PollMethod.YES_NO, null, N.plusSeconds(1)),
                                    N))
                    .isNotNull();
        }

        @Test
        @DisplayName("TC-6091: 미응답자는 주어진 현재 크루원 중 응답이 없는 회원")
        void nonRespondents() {
            PollPost poll = choices(3);
            poll.respond(2, 1, N);
            poll.respond(4, 2, N);
            assertThat(poll.nonRespondents(List.of(1L, 2L, 3L, 4L))).containsExactly(1L, 3L);
            PollPost all = choices(2);
            all.respond(1, 1, N);
            all.respond(2, 1, N);
            assertThat(all.nonRespondents(List.of(1L, 2L))).isEmpty();
            assertThat(all.nonRespondents(List.of())).isEmpty();
            all.markReminded();
            assertThat(all.isReminded()).isTrue();
        }
    }

    @Test
    @DisplayName("TC-6097: 공지 본문 1,000자는 받고 1,001자는 BODY_TOO_LONG, 고정·해제, 빈 공지는 400")
    void notice() {
        NoticePost notice = new NoticePost(CREW, 2, new NoticeDraft("가".repeat(1000), false), N);
        assertThatThrownBy(
                        () -> new NoticePost(CREW, 2, new NoticeDraft("가".repeat(1001), false), N))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.BODY_TOO_LONG));
        assertThatThrownBy(() -> new NoticeDraft(" ", false))
                .isInstanceOf(IllegalArgumentException.class);
        notice.pin();
        assertThat(notice.summary(1).notice().pinned()).isTrue();
        notice.unpin();
        assertThat(notice.isPinned()).isFalse();
    }

    @Test
    @DisplayName(
            "TC-6098: 게시글 빈 글·공백만이면 POST_EMPTY, 6장 PHOTO_LIMIT, 1,001자 BODY_TOO_LONG, 사진 메타 seq"
                    + " 1~5")
    void board() {
        PhotoUpload photo = new PhotoUpload(JpegSamples.clean(new byte[10]));
        assertThatThrownBy(() -> new BoardPost(CREW, 3, new BoardDraft("", List.of()), N))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.POST_EMPTY));
        assertThatThrownBy(() -> new BoardPost(CREW, 3, new BoardDraft("   ", List.of()), N))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.POST_EMPTY));
        assertThat(new BoardPost(CREW, 3, new BoardDraft("", List.of(photo)), N).photos())
                .hasSize(1);
        BoardPost five =
                new BoardPost(
                        CREW,
                        3,
                        new BoardDraft("가".repeat(1000), Collections.nCopies(5, photo)),
                        N);
        assertThat(five.photos()).extracting(PostPhoto::seq).containsExactly(1, 2, 3, 4, 5);
        assertThat(five.photos())
                .allSatisfy(p -> assertThat(p.sizeBytes()).isEqualTo(photo.size()));
        assertThatThrownBy(
                        () ->
                                new BoardPost(
                                        CREW,
                                        3,
                                        new BoardDraft("글", Collections.nCopies(6, photo)),
                                        N))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.PHOTO_LIMIT));
        assertThatThrownBy(
                        () ->
                                new BoardPost(
                                        CREW, 3, new BoardDraft("가".repeat(1001), List.of()), N))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.BODY_TOO_LONG));
    }

    @Test
    @DisplayName("TC-6101: 종류별 본문 상한 — 응원 200, 공지·게시글 1,000")
    void maxBody() {
        assertThat(new CheerPost(CREW, 3, "가".repeat(200), N).body()).hasSize(200);
        assertThatThrownBy(() -> new CheerPost(CREW, 3, "가".repeat(201), N))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.BODY_TOO_LONG));
        assertThatThrownBy(() -> new CheerPost(CREW, 3, " ", N))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new BoardPost(CREW, 3, new BoardDraft("가".repeat(1000), List.of()), N).body())
                .hasSize(1000);
        assertThatThrownBy(
                        () ->
                                new CrewRunPost(
                                        CREW,
                                        3,
                                        new CrewRunDraft(
                                                "가".repeat(201), MEET, StandardDistance.KM5, null),
                                        N))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.BODY_TOO_LONG));
    }

    /** 서로게이트 쌍 하나(UTF-16 2단위, 코드 포인트 1자). */
    private static final String EMOJI = "\uD83C\uDFC3";

    private static String emojis(int count) {
        return EMOJI.repeat(count);
    }

    @Test
    @DisplayName("D-143: 본문 상한은 코드 포인트로 센다 — 이모지 공지·게시글 1,000자 허용, 1,001자 BODY_TOO_LONG")
    void longBody_countsCodePoints() {
        assertThat(new NoticePost(CREW, 2, new NoticeDraft(emojis(1000), false), N).body())
                .hasSize(2000);
        assertThatThrownBy(() -> new NoticePost(CREW, 2, new NoticeDraft(emojis(1001), false), N))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.BODY_TOO_LONG));
        assertThat(new BoardPost(CREW, 3, new BoardDraft(emojis(1000), List.of()), N).isVisible())
                .isTrue();
        assertThatThrownBy(() -> new BoardPost(CREW, 3, new BoardDraft(emojis(1001), List.of()), N))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.BODY_TOO_LONG));
    }

    @Test
    @DisplayName("D-143: 응원·크루런·대면 모임 본문 200자, 투표 질문 200자·선택지 40자, 장소명 50자도 코드 포인트")
    void shortTexts_countCodePoints() {
        assertThat(new CheerPost(CREW, 3, emojis(200), N).isVisible()).isTrue();
        assertThatThrownBy(() -> new CheerPost(CREW, 3, emojis(201), N))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.BODY_TOO_LONG));
        assertThat(
                        new CrewRunPost(
                                        CREW,
                                        3,
                                        new CrewRunDraft(
                                                emojis(200), MEET, StandardDistance.KM5, null),
                                        N)
                                .isVisible())
                .isTrue();
        assertThat(new MeetupPost(CREW, 3, new MeetupDraft(emojis(200), MEET, emojis(50), null), N))
                .isNotNull();
        assertThatThrownBy(() -> new MeetupDraft("", MEET, emojis(51), null))
                .isInstanceOf(IllegalArgumentException.class);

        LocalDateTime closes = N.plusDays(1);
        PollPost poll =
                new PollPost(
                        CREW,
                        3,
                        new PollDraft(
                                emojis(200), PollMethod.CHOICES, List.of(emojis(40), "나"), closes),
                        N);
        assertThat(poll.isVisible()).isTrue();
        assertThatThrownBy(() -> new PollDraft(emojis(201), PollMethod.YES_NO, null, closes))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new PollDraft(
                                        "질문", PollMethod.CHOICES, List.of(emojis(41), "나"), closes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-6102: 작성자 SELF, 크루장·운영진 FORCED, 크루원·크루 밖은 거부, 작성자 판정이 먼저")
    void deleteDecision() {
        assertThat(new CheerPost(CREW, 3, "응원", N).delete(3, CrewRelation.MEMBER))
                .isEqualTo(DeletionKind.SELF);
        assertThat(new CheerPost(CREW, 3, "응원", N).delete(1, CrewRelation.LEADER))
                .isEqualTo(DeletionKind.FORCED);
        assertThat(new CheerPost(CREW, 3, "응원", N).delete(2, CrewRelation.MANAGER))
                .isEqualTo(DeletionKind.FORCED);
        assertThatThrownBy(() -> new CheerPost(CREW, 3, "응원", N).delete(4, CrewRelation.MEMBER))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> new CheerPost(CREW, 3, "응원", N).delete(9, CrewRelation.OUTSIDER))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(new CheerPost(CREW, 2, "응원", N).delete(2, CrewRelation.MANAGER))
                .isEqualTo(DeletionKind.SELF);
    }

    @Test
    @DisplayName("TC-6103: 숨김·해제·삭제에 따라 isVisible이 바뀐다")
    void hideUnhide() {
        CheerPost post = new CheerPost(CREW, 3, "응원", N);
        assertThat(post.isVisible()).isTrue();
        post.hide();
        assertThat(post.isVisible()).isFalse();
        post.unhide();
        assertThat(post.isVisible()).isTrue();
        post.delete(3, CrewRelation.MEMBER);
        assertThat(post.isVisible()).isFalse();
        assertThat(Set.of(post.kind())).containsExactly(PostKind.CHEER);
    }
}
