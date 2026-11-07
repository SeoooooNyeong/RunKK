package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.crew.live.CrewRunCancelled;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * v0.11 crew 응용 서비스(S3a, test 프로필 H2 + 내장 Redis): 운영진 지정·해제(TC-6058), 공지·고정(TC-6093~TC-6096), 목록
 * 권한(TC-6105), 삭제(TC-6104), 참석 응답(TC-6084), 투표(TC-6092), 게시글 사진·앨범(TC-6106, TC-6216), 소식
 * 알림(TC-6107), 투표 마감 알림(TC-6169~6171), 숨김 해제(TC-6221), 탈퇴·강퇴 응답 정리(TC-6222·6223), 탈퇴 정리(TC-6051),
 * 크루 삭제의 크루런 종료(K-2), 공동 목표(TC-6138~6146).
 */
@SpringBootTest(classes = RunkkApplication.class)
@ActiveProfiles("test")
@DisplayName("crew v0.11 응용 서비스 흐름(S3a)")
class CrewV011ScenarioTest {

    private static final AtomicInteger NAMES = new AtomicInteger();

    @MockitoSpyBean CrewRunMessenger messenger;
    @MockitoSpyBean PostPhotoStore photoStore;
    @Autowired CrewService crews;
    @Autowired MembershipService membership;
    @Autowired CrewPostService posts;
    @Autowired CrewRunService crewRuns;
    @Autowired WeeklyRaceService race;
    @Autowired CrewWithdrawalListener withdrawal;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    /** 크루 7 모양: L 크루장, M 운영진, R1·R2 크루원, O 크루 밖. */
    private record Crew7(long id, long l, long m, long r1, long r2, long o) {}

    private long member() {
        String name = "삼에이" + NAMES.incrementAndGet();
        return tx.execute(s -> members.save(MemberFixtures.member(name)).getId());
    }

    private Crew7 crew7() {
        long l = member();
        long id =
                crews.create(
                                l,
                                new CrewCreateCommand(
                                        "삼에이크루" + NAMES.incrementAndGet(),
                                        null,
                                        Set.of(PersonalityTag.SOCIAL_RUN),
                                        CrewVisibility.PUBLIC,
                                        JoinPolicy.FREE,
                                        null))
                        .crewId();
        long m = member();
        long r1 = member();
        long r2 = member();
        for (long x : new long[] {m, r1, r2}) {
            membership.apply(x, JoinTarget.byCrew(id));
        }
        crews.appointManager(l, id, m);
        return new Crew7(id, l, m, r1, r2, member());
    }

    private int notifications(long recipient, String type) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_id = ? and type = ?",
                Integer.class,
                recipient,
                type);
    }

    private int audits(String action, String role, String target) {
        return jdbc.queryForObject(
                "select count(*) from audit_record where action = ? and actor_role = ?"
                        + " and target_id = ?",
                Integer.class,
                action,
                role,
                target);
    }

    private static ReasonCode reasonOf(Throwable e) {
        return ((BusinessRuleException) e).reason();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    private long meetup(long author, long crewId, Integer capacity) {
        return posts.writeMeetup(
                        author, crewId, new MeetupDraft("모여요", now().plusDays(1), "한강공원", capacity))
                .postId();
    }

    private long poll(long author, long crewId, LocalDateTime closesAt) {
        return posts.writePoll(
                        author,
                        crewId,
                        new PollDraft("어디서?", PollMethod.CHOICES, List.of("가", "나", "다"), closesAt))
                .postId();
    }

    private void hide(long postId) {
        posts.hideReported(postId);
    }

    @Test
    @DisplayName("TC-6058·TC-6801: 운영진 지정·해제는 크루장만, 감사·MANAGER_CHANGED, 상한 2")
    void managers() {
        Crew7 c = crew7();
        assertThatThrownBy(() -> crews.appointManager(c.m(), c.id(), c.r1()))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(crews.appointManager(c.l(), c.id(), c.r1())).isEqualTo(CrewRole.MANAGER);
        assertThat(audits("MANAGER_APPOINT", "CREW_LEADER", String.valueOf(c.r1()))).isEqualTo(1);
        assertThat(notifications(c.r1(), "MANAGER_CHANGED")).isEqualTo(1);
        assertThatThrownBy(() -> crews.appointManager(c.l(), c.id(), c.r2()))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.MANAGER_LIMIT));
        assertThat(crews.dismissManager(c.l(), c.id(), c.r1())).isEqualTo(CrewRole.MEMBER);
        assertThat(audits("MANAGER_DISMISS", "CREW_LEADER", String.valueOf(c.r1()))).isEqualTo(1);
        assertThat(notifications(c.r1(), "MANAGER_CHANGED")).isEqualTo(2);
        assertThatThrownBy(() -> crews.dismissManager(c.l(), c.id(), c.r2()))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TARGET_INVALID));
    }

    @Test
    @DisplayName(
            "TC-6093·TC-6094·TC-6095·TC-6107: 공지는 L·M만, 고정 3개 한도·재고정 멱등, 작성자 뺀 크루원에게 CREW_NOTICE")
    void noticesAndPins() {
        Crew7 c = crew7();
        assertThatThrownBy(() -> posts.writeNotice(c.r1(), c.id(), new NoticeDraft("공지", false)))
                .isInstanceOf(PermissionDeniedException.class);
        long p1 = posts.writeNotice(c.m(), c.id(), new NoticeDraft("공지1", true)).postId();
        posts.writeNotice(c.m(), c.id(), new NoticeDraft("공지2", true));
        long p3 = posts.writeNotice(c.l(), c.id(), new NoticeDraft("공지3", true)).postId();
        assertThat(notifications(c.l(), "CREW_NOTICE")).isEqualTo(2);
        assertThat(notifications(c.m(), "CREW_NOTICE")).isEqualTo(1);
        assertThat(notifications(c.r1(), "CREW_NOTICE")).isEqualTo(3);
        assertThatThrownBy(() -> posts.writeNotice(c.m(), c.id(), new NoticeDraft("넷째", true)))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.PIN_LIMIT));
        long free = posts.writeNotice(c.m(), c.id(), new NoticeDraft("고정 안 함", false)).postId();
        assertThatThrownBy(() -> posts.pin(c.l(), free, true))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.PIN_LIMIT));
        assertThat(posts.pin(c.l(), p3, true)).as("이미 고정된 공지는 그대로 성공").isTrue();
        assertThatThrownBy(() -> posts.pin(c.r1(), free, true))
                .isInstanceOf(PermissionDeniedException.class);
        long cheer = posts.writeCheer(c.r1(), c.id(), "응원").postId();
        assertThatThrownBy(() -> posts.pin(c.l(), cheer, true))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TARGET_INVALID));
        assertThat(notifications(c.l(), "CREW_NOTICE")).as("응원글은 알리지 않는다").isEqualTo(3);

        // TC-6096·TC-6221: 숨긴 고정 공지는 세지 않고, 다시 보일 때 한도가 차 있으면 고정을 푼다
        hide(p1);
        assertThat(posts.pin(c.l(), free, true)).isTrue();
        posts.unhideReported(p1);
        PostListView pinned =
                posts.list(c.r1(), c.id(), new PostQuery(Set.of(), true, PageCursor.all()));
        assertThat(pinned.posts()).hasSize(3);
        assertThat(pinned.posts()).noneMatch(l -> l.summary().postId() == p1);
        assertThat(posts.pin(c.l(), p3, false)).isFalse();
    }

    @Test
    @DisplayName("TC-6105·TC-6104: 목록은 삭제·숨김을 빼고 종류·고정으로 거르며 canDelete는 본인 글 또는 L·M, 남의 글 삭제는 감사")
    void listAndDelete() {
        Crew7 c = crew7();
        long cheer = posts.writeCheer(c.r2(), c.id(), "응원").postId();
        long meet = meetup(c.r1(), c.id(), null);
        long pollId = poll(c.r1(), c.id(), now().plusDays(1));
        posts.writeNotice(c.m(), c.id(), new NoticeDraft("고정", true));
        posts.writeNotice(c.m(), c.id(), new NoticeDraft("보통", false));
        long gone = posts.writeCheer(c.r1(), c.id(), "지울 글").postId();
        posts.delete(c.r1(), gone);
        long hidden = posts.writeCheer(c.r1(), c.id(), "숨길 글").postId();
        hide(hidden);

        PostListView all = posts.list(c.r2(), c.id(), PostQuery.all());
        assertThat(all.posts()).hasSize(5);
        assertThat(all.posts())
                .filteredOn(PostListView.Line::canDelete)
                .extracting(l -> l.summary().postId())
                .containsExactly(cheer);
        assertThat(posts.list(c.l(), c.id(), PostQuery.all()).posts())
                .allMatch(PostListView.Line::canDelete);
        assertThat(
                        posts.list(
                                        c.r2(),
                                        c.id(),
                                        new PostQuery(
                                                Set.of(PostKind.MEETUP, PostKind.POLL),
                                                false,
                                                PageCursor.all()))
                                .posts())
                .extracting(l -> l.summary().postId())
                .containsExactlyInAnyOrder(meet, pollId);
        assertThat(
                        posts.list(c.r2(), c.id(), new PostQuery(Set.of(), true, PageCursor.all()))
                                .posts())
                .singleElement()
                .satisfies(l -> assertThat(l.summary().notice().pinned()).isTrue());
        PostListView.Line meetLine =
                all.posts().stream()
                        .filter(l -> l.summary().postId() == meet)
                        .findFirst()
                        .orElseThrow();
        assertThat(meetLine.summary().meetup().counts()).isEqualTo(new AttendanceCounts(1, 0, 0));

        assertThatThrownBy(() -> posts.delete(c.r1(), hidden))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.POST_NOT_FOUND));
        assertThatThrownBy(() -> posts.delete(c.r1(), cheer))
                .isInstanceOf(PermissionDeniedException.class);
        posts.delete(c.m(), cheer);
        assertThat(audits("POST_FORCE_DELETE", "CREW_MANAGER", String.valueOf(cheer))).isEqualTo(1);
        long other = posts.writeCheer(c.r2(), c.id(), "응원2").postId();
        posts.delete(c.l(), other);
        assertThat(audits("POST_FORCE_DELETE", "CREW_LEADER", String.valueOf(other))).isEqualTo(1);
        long own = posts.writeCheer(c.r2(), c.id(), "응원3").postId();
        posts.delete(c.r2(), own);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from audit_record where target_id = ?",
                                Integer.class,
                                String.valueOf(own)))
                .isZero();
    }

    @Test
    @DisplayName(
            "TC-6084·TC-6107: 참석 응답은 숨김·삭제면 POST_NOT_FOUND, 일정 아님 TARGET_INVALID, 크루 밖 거부, 대면 모임"
                    + " 작성은 CREW_SCHEDULE")
    void attendance() {
        Crew7 c = crew7();
        long meet = meetup(c.r1(), c.id(), 3);
        assertThat(notifications(c.r2(), "CREW_SCHEDULE")).isEqualTo(1);
        assertThat(notifications(c.r1(), "CREW_SCHEDULE")).isZero();
        AttendanceView view = posts.answerAttendance(c.r2(), meet, AttendanceAnswer.GOING);
        assertThat(view.goingCount()).isEqualTo(2);
        posts.answerAttendance(c.m(), meet, AttendanceAnswer.GOING);
        assertThatThrownBy(() -> posts.answerAttendance(c.l(), meet, AttendanceAnswer.GOING))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.SCHEDULE_FULL));
        assertThat(posts.answerAttendance(c.r2(), meet, AttendanceAnswer.UNDECIDED))
                .isEqualTo(new AttendanceView(meet, AttendanceAnswer.UNDECIDED, 2, 0, 1));

        assertThatThrownBy(() -> posts.answerAttendance(c.o(), meet, AttendanceAnswer.GOING))
                .isInstanceOf(PermissionDeniedException.class);
        long cheer = posts.writeCheer(c.r1(), c.id(), "응원").postId();
        assertThatThrownBy(() -> posts.answerAttendance(c.r1(), cheer, AttendanceAnswer.GOING))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TARGET_INVALID));
        hide(meet);
        assertThatThrownBy(() -> posts.answerAttendance(c.r2(), meet, AttendanceAnswer.GOING))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.POST_NOT_FOUND));

        long run =
                posts.writeCrewRun(
                                c.r1(),
                                c.id(),
                                new CrewRunDraft(
                                        null, now().plusDays(2), StandardDistance.KM5, null))
                        .postId();
        assertThat(notifications(c.r2(), "CREW_RUN_RECRUIT")).isEqualTo(1);
        assertThat(notifications(c.r2(), "CREW_SCHEDULE")).as("크루런은 CREW_SCHEDULE 0").isEqualTo(1);
        assertThat(posts.answerAttendance(c.r2(), run, AttendanceAnswer.UNDECIDED))
                .isEqualTo(new AttendanceView(run, AttendanceAnswer.UNDECIDED, 1, 0, 1));
    }

    @Test
    @DisplayName("TC-6092·TC-6107: 투표 응답·결과는 익명이 아니고 크루 밖은 거부, 작성 때 CREW_POLL")
    void polls() {
        Crew7 c = crew7();
        long p = poll(c.r1(), c.id(), now().plusDays(1));
        assertThat(notifications(c.l(), "CREW_POLL")).isEqualTo(1);
        posts.respondPoll(c.r2(), p, 1);
        PollResultView result = posts.respondPoll(c.m(), p, 2);
        assertThat(result.options())
                .extracting(PollResultView.Option::count)
                .containsExactly(1, 1, 0);
        assertThat(result.options().get(0).respondents())
                .singleElement()
                .satisfies(r -> assertThat(r.memberId()).isEqualTo(c.r2()));
        assertThat(result.myOptionNo()).isEqualTo(2);
        assertThat(posts.pollResult(c.l(), p).myOptionNo()).isNull();
        assertThatThrownBy(() -> posts.respondPoll(c.o(), p, 1))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> posts.respondPoll(c.r1(), p, 9))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.POLL_INVALID));
    }

    @Test
    @DisplayName(
            "TC-6100·TC-6106·TC-6216·TC-6051: 게시글 사진은 위치 메타데이터를 지운 바이트만 저장, 앨범·사진은 보이는 글만, 탈퇴하면 사진"
                    + " 삭제")
    void boardPhotos() {
        Crew7 c = crew7();
        byte[] withGps = JpegSamples.withLocation(new byte[64]);
        long board =
                posts.writeBoard(
                                c.r1(),
                                c.id(),
                                new BoardDraft(
                                        "사진",
                                        List.of(
                                                new PhotoUpload(withGps),
                                                new PhotoUpload(withGps))))
                        .postId();
        verify(photoStore, org.mockito.Mockito.times(2))
                .save(anyLong(), argThat(b -> JpegSamples.count(b, "GPS") == 0));
        long hiddenBoard =
                posts.writeBoard(
                                c.r1(),
                                c.id(),
                                new BoardDraft("숨김", List.of(new PhotoUpload(withGps))))
                        .postId();
        assertThat(notifications(c.r2(), "CREW_NOTICE") + notifications(c.r2(), "CREW_POLL"))
                .as("게시글은 알리지 않는다")
                .isZero();
        hide(hiddenBoard);

        AlbumView album = posts.album(c.r2(), c.id(), PageCursor.all());
        assertThat(album.photos()).hasSize(2).allMatch(p -> p.postId() == board);
        assertThat(album.photos().get(0).photoId()).isGreaterThan(album.photos().get(1).photoId());
        long photoId = album.photos().get(0).photoId();
        PhotoContent content = posts.photo(c.r2(), photoId);
        assertThat(content.jpeg()).isEqualTo(JpegSamples.clean(new byte[64]));
        assertThatThrownBy(() -> posts.photo(c.o(), photoId))
                .isInstanceOf(PermissionDeniedException.class);
        long hiddenPhoto =
                jdbc.queryForObject(
                        "select id from crew_photo where post_id = ?", Long.class, hiddenBoard);
        assertThatThrownBy(() -> posts.photo(c.r2(), hiddenPhoto))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.POST_NOT_FOUND));
        PostListView.Line line =
                posts.list(c.r2(), c.id(), PostQuery.all()).posts().stream()
                        .filter(l -> l.summary().postId() == board)
                        .findFirst()
                        .orElseThrow();
        assertThat(line.summary().photoIds()).hasSize(2);

        withdrawal.onMemberWithdrawn(new MemberWithdrawn(c.r1(), now()));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_photo where post_id = ?",
                                Integer.class,
                                board))
                .isZero();
    }

    @Test
    @DisplayName("TC-6100 ②: 둘째 사진 저장이 실패하면 글·메타가 함께 롤백된다")
    void boardRollsBackOnStoreFailure() {
        Crew7 c = crew7();
        PhotoUpload photo = new PhotoUpload(JpegSamples.clean(new byte[8]));
        org.mockito.Mockito.doCallRealMethod()
                .doThrow(new IllegalStateException("디스크"))
                .when(photoStore)
                .save(anyLong(), any());
        int before = jdbc.queryForObject("select count(*) from crew_photo", Integer.class);
        assertThatThrownBy(
                        () ->
                                posts.writeBoard(
                                        c.r1(), c.id(), new BoardDraft("글", List.of(photo, photo))))
                .hasMessageContaining("디스크");
        org.mockito.Mockito.reset(photoStore);
        assertThat(jdbc.queryForObject("select count(*) from crew_photo", Integer.class))
                .isEqualTo(before);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_post where crew_id = ? and post_kind ="
                                        + " 'BOARD'",
                                Integer.class,
                                c.id()))
                .isZero();
    }

    @Test
    @DisplayName("TC-6169·TC-6170: 마감 1시간 안의 보이는 투표만 미응답 현재 크루원에게 한 번 알린다")
    void pollReminders() {
        Crew7 c = crew7();
        LocalDateTime now = now();
        long due = poll(c.r1(), c.id(), now.plusMinutes(30));
        long hidden = poll(c.r1(), c.id(), now.plusMinutes(40));
        hide(hidden);
        poll(c.r1(), c.id(), now.plusHours(2));
        posts.respondPoll(c.m(), due, 1);

        int reminded = posts.remindClosingPolls(now);
        assertThat(reminded).isGreaterThanOrEqualTo(1);
        assertThat(notifications(c.r1(), "POLL_CLOSING_SOON")).isEqualTo(1);
        assertThat(notifications(c.l(), "POLL_CLOSING_SOON")).isEqualTo(1);
        assertThat(notifications(c.m(), "POLL_CLOSING_SOON")).as("응답자 제외").isZero();
        posts.remindClosingPolls(now);
        assertThat(notifications(c.r1(), "POLL_CLOSING_SOON")).as("한 번만").isEqualTo(1);
    }

    @Test
    @DisplayName("TC-6222·TC-6223: 탈퇴·강퇴는 시작 전 일정·마감 전 투표의 응답만 지우고 지난 것은 남긴다, 운영진 강퇴는 거부")
    void leaveCleansOpenResponses() {
        Crew7 c = crew7();
        long meet = meetup(c.r1(), c.id(), null);
        long open = poll(c.r1(), c.id(), now().plusDays(1));
        posts.answerAttendance(c.r2(), meet, AttendanceAnswer.GOING);
        posts.respondPoll(c.r2(), open, 1);
        jdbc.update(
                "insert into crew_poll_response (post_id, member_id, option_no, responded_at)"
                        + " values (?, ?, 1, ?)",
                open,
                c.m(),
                now());
        long closed = poll(c.r1(), c.id(), now().plusDays(1));
        posts.respondPoll(c.r2(), closed, 2);
        jdbc.update(
                "update crew_post set poll_closes_at = ? where id = ?", now().minusDays(1), closed);

        assertThatThrownBy(() -> membership.kick(c.m(), c.id(), c.r2()))
                .isInstanceOf(PermissionDeniedException.class);
        membership.leave(c.r2(), c.id());

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_meetup_attendance where post_id = ?",
                                Integer.class,
                                meet))
                .isEqualTo(1);
        assertThat(posts.pollResult(c.r1(), open).options().get(0).count()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_poll_response where post_id = ?",
                                Integer.class,
                                closed))
                .as("마감된 투표 응답은 남는다")
                .isEqualTo(1);

        posts.answerAttendance(c.m(), meet, AttendanceAnswer.GOING);
        membership.kick(c.l(), c.id(), c.m());
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_meetup_attendance where post_id = ?",
                                Integer.class,
                                meet))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("K-2(D-66): 크루 삭제는 대기실이 열린 크루런의 입장자에게 커밋 뒤 CrewRunCancelled를 보낸다")
    void crewDeletionEndsCrewRuns() {
        Crew7 c = crew7();
        long run =
                posts.writeCrewRun(
                                c.r1(),
                                c.id(),
                                new CrewRunDraft(
                                        null, now().plusMinutes(10), StandardDistance.KM3, null))
                        .postId();
        crewRuns.enterLobby(c.r1(), run);
        crews.delete(c.l(), c.id());
        verify(messenger)
                .sendAll(
                        argThat(ids -> ids.contains(c.r1())),
                        eq(new CrewRunCancelled(run, CrewRunAnnouncer.DELETED_REASON)));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_post where id = ?", Integer.class, run))
                .isZero();
    }

    @Test
    @DisplayName("F-14c: 준비 완료가 최소 준비 인원보다 적으면 그 인원을 넣은 문구로 CrewRunCancelled를 보낸다")
    void crewRunCancelledReasonCarriesMinReady() {
        Crew7 c = crew7();
        LocalDateTime at = now().plusMinutes(20).withNano(0);
        long run =
                posts.writeCrewRun(
                                c.r1(),
                                c.id(),
                                new CrewRunDraft(null, at, StandardDistance.KM3, null))
                        .postId();
        crewRuns.enterLobby(c.r1(), run);
        crewRuns.toggleReady(c.r1(), run);
        crewRuns.tick(at);
        verify(messenger)
                .sendAll(
                        argThat(ids -> ids.contains(c.r1())),
                        eq(new CrewRunCancelled(run, "준비 완료한 참가자가 2명보다 적어 시작하지 않았어요")));
    }

    private RunRecordSaved saved(long memberId, long meters, LocalDateTime startedAt) {
        return new RunRecordSaved(
                UUID.randomUUID().toString(),
                memberId,
                startedAt,
                Distance.ofMeters(meters),
                Duration.ofMinutes(30),
                true,
                null,
                RunMode.FREE,
                null,
                null,
                true,
                1,
                startedAt);
    }

    @Test
    @DisplayName("TC-6138~TC-6145: 목표는 L·M만, 정한 즉시 달성, 달성은 한 번, 다시 정해도 재판정 없음, 축하 카드")
    void goals() {
        Crew7 c = crew7();
        LocalDateTime now = now();
        assertThatThrownBy(() -> race.setGoal(c.r1(), c.id(), Distance.ofMeters(10_000), now))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> race.setGoal(c.m(), c.id(), Distance.ofMeters(999), now))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.GOAL_INVALID));

        race.onRunSaved(saved(c.r1(), 7_500, now()));
        RaceStatusView set = race.setGoal(c.m(), c.id(), Distance.ofMeters(10_000), now);
        assertThat(set.goal()).isEqualTo(Distance.ofMeters(10_000));
        assertThat(set.goalProgressPercent()).isEqualTo(75);
        assertThat(set.achievedAt()).isNull();
        assertThat(set.celebration()).isNull();

        race.onRunSaved(saved(c.r2(), 3_000, now()));
        RaceStatusView done = race.status(c.l(), c.id(), Optional.empty());
        assertThat(done.achievedAt()).isNotNull();
        assertThat(done.goalProgressPercent()).isEqualTo(100);
        assertThat(done.celebration().participantCount()).isEqualTo(2);
        assertThat(done.celebration().goalMeters()).isEqualTo(Distance.ofMeters(10_000));
        for (long x : new long[] {c.l(), c.m(), c.r1(), c.r2()}) {
            assertThat(notifications(x, "CREW_GOAL_ACHIEVED")).isEqualTo(1);
        }
        race.onRunSaved(saved(c.r1(), 1_000, now()));
        RaceStatusView again = race.setGoal(c.l(), c.id(), Distance.ofMeters(5_000), now);
        assertThat(again.achievedAt()).isEqualTo(done.achievedAt());
        assertThat(notifications(c.r1(), "CREW_GOAL_ACHIEVED")).as("한 주에 한 번").isEqualTo(1);
    }

    @Test
    @DisplayName("TC-6146·TC-6148: 지난 주 기록은 지난 주로 판정하고, 새 주 행은 직전 목표를 이어 쓴다")
    void lateRecordAndCarryOver() {
        Crew7 c = crew7();
        LocalDateTime now = now();
        LocalDate lastMonday = com.a207.runkk.common.Week.of(now).previous().monday();
        jdbc.update(
                "insert into weekly_race (crew_id, week_start, total_meters, goal_meters)"
                        + " values (?, ?, 0, 2000)",
                c.id(),
                lastMonday);
        jdbc.update(
                "update crew_membership set joined_at = ? where crew_id = ?",
                lastMonday.atStartOfDay().minusDays(1),
                c.id());
        race.onRunSaved(saved(c.r1(), 2_500, lastMonday.atTime(8, 0)));
        assertThat(race.status(c.l(), c.id(), Optional.of(lastMonday)).achievedAt()).isNotNull();
        assertThat(notifications(c.r2(), "CREW_GOAL_ACHIEVED")).isEqualTo(1);
        assertThat(race.status(c.l(), c.id(), Optional.empty()).achievedAt()).isNull();

        race.onRunSaved(saved(c.r1(), 500, now()));
        assertThat(race.status(c.l(), c.id(), Optional.empty()).goal())
                .as("이번 주 첫 행은 지난 주 목표를 이어 쓴다")
                .isEqualTo(Distance.ofMeters(2_000));
        verify(messenger, org.mockito.Mockito.never()).sendAll(anyCollection(), any());
    }
}
