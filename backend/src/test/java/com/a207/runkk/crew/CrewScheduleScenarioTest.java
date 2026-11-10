package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.CrewTag;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** W6 크루 일정 응용 서비스(S15P31A207-297, test 프로필 H2): 상세·수정·반복 회차 생성·지난 일정 연월 조회·참석 두 상태. */
@SpringBootTest(classes = RunkkApplication.class)
@ActiveProfiles("test")
@DisplayName("크루 일정 응용 서비스(W6)")
class CrewScheduleScenarioTest {

    private static final AtomicInteger NAMES = new AtomicInteger();

    @Autowired CrewService crews;
    @Autowired MembershipService membership;
    @Autowired CrewPostService posts;
    @Autowired CrewScheduleService schedules;
    @Autowired CrewRunRepeater repeater;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    /** 크루장 l, 크루원 a·b, 크루 밖 o. */
    private record Crew3(long id, long l, long a, long b, long o) {}

    private long member() {
        String name = "일정" + NAMES.incrementAndGet();
        return tx.execute(s -> members.save(MemberFixtures.member(name)).getId());
    }

    private Crew3 crew() {
        long l = member();
        long id =
                crews.create(
                                l,
                                new CrewCreateCommand(
                                        "일정크루" + NAMES.incrementAndGet(),
                                        null,
                                        Set.of(CrewTag.WEEKDAY_EVENING),
                                        CrewVisibility.PUBLIC,
                                        JoinPolicy.FREE,
                                        null))
                        .crewId();
        long a = member();
        long b = member();
        membership.apply(a, JoinTarget.byCrew(id));
        membership.apply(b, JoinTarget.byCrew(id));
        return new Crew3(id, l, a, b, member());
    }

    /** 내일 같은 시각(분 0). */
    private static LocalDateTime tomorrow() {
        return LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0);
    }

    private long write(long author, long crewId, LocalDateTime at, CrewRunRepeat repeat) {
        return posts.writeCrewRun(
                        author,
                        crewId,
                        new CrewRunDraft("토요일 한강", "8번 출구", at, StandardDistance.KM5, null, repeat))
                .postId();
    }

    private long meetup(long author, long crewId) {
        return posts.writeMeetup(author, crewId, new MeetupDraft("모여요", tomorrow(), "한강", 5))
                .postId();
    }

    /** at이 든 주(월~일)의 일요일 오전 9시: 반복 작업(D-성-26, 코드 리뷰 M6)이 다음 주 회차를 만드는 시각. */
    private static LocalDateTime sundayOf(LocalDateTime at) {
        return at.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(9, 0);
    }

    private static ReasonCode reasonOf(Throwable e) {
        return ((BusinessRuleException) e).reason();
    }

    private int postsOfCrew(long crewId) {
        return jdbc.queryForObject(
                "select count(*) from crew_post where crew_id = ? and post_kind = 'CREW_RUN'"
                        + " and deleted = false",
                Integer.class,
                crewId);
    }

    @Test
    @DisplayName("상세: 제목·내용·거리·참석자(작성자 먼저, 나 표시), 참석 취소는 목록에서 빠진다, 크루 밖은 403")
    void detail() {
        Crew3 c = crew();
        long id = write(c.l(), c.id(), tomorrow(), CrewRunRepeat.NONE);
        posts.answerAttendance(c.b(), id, AttendanceAnswer.GOING);
        posts.answerAttendance(c.a(), id, AttendanceAnswer.GOING);

        CrewRunDetailView v = schedules.detail(c.a(), id);
        assertThat(v.title()).isEqualTo("토요일 한강");
        assertThat(v.body()).isEqualTo("8번 출구");
        assertThat(v.distance()).isEqualTo(StandardDistance.KM5);
        assertThat(v.goingCount()).isEqualTo(3);
        assertThat(v.myAnswer()).isEqualTo(AttendanceAnswer.GOING);
        assertThat(v.mine()).isFalse();
        assertThat(v.canEdit()).isFalse();
        assertThat(v.canDelete()).isFalse();
        assertThat(v.repeat()).isEqualTo(CrewRunRepeat.NONE);
        assertThat(v.repeatDayOfWeek()).isNull();
        assertThat(v.attendees())
                .extracting(CrewRunDetailView.Attendee::memberId)
                .containsExactly(c.l(), c.b(), c.a());
        assertThat(v.attendees().get(0).author()).isTrue();
        assertThat(v.attendees().get(2).me()).isTrue();

        schedules.withdrawAttendance(c.b(), id);
        CrewRunDetailView mine = schedules.detail(c.l(), id);
        assertThat(mine.goingCount()).isEqualTo(2);
        assertThat(mine.attendees())
                .extracting(CrewRunDetailView.Attendee::memberId)
                .containsExactly(c.l(), c.a());
        assertThat(mine.canEdit()).isTrue();
        assertThat(mine.canDelete()).isTrue();
        assertThat(schedules.detail(c.b(), id).myAnswer()).as("응답을 거뒀다").isNull();
        assertThatThrownBy(() -> schedules.withdrawAttendance(c.a(), meetup(c.l(), c.id())))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TARGET_INVALID));

        assertThatThrownBy(() -> schedules.detail(c.o(), id))
                .isInstanceOf(PermissionDeniedException.class);
        long cheer = posts.writeCheer(c.l(), c.id(), "응원").postId();
        assertThatThrownBy(() -> schedules.detail(c.l(), cheer))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.POST_NOT_FOUND));
    }

    @Test
    @DisplayName("수정: 작성자만 제목·내용·거리를 바꾸고 결과 상세를 돌려준다, 남은 403, 삭제된 글은 POST_NOT_FOUND")
    void edit() {
        Crew3 c = crew();
        long id = write(c.l(), c.id(), tomorrow(), CrewRunRepeat.NONE);
        CrewRunDetailView v =
                schedules.edit(
                        c.l(),
                        id,
                        new CrewRunEdit("한강 하프", "천천히", StandardDistance.HALF, CrewRunRepeat.NONE));
        assertThat(v.title()).isEqualTo("한강 하프");
        assertThat(v.body()).isEqualTo("천천히");
        assertThat(v.distance()).isEqualTo(StandardDistance.HALF);
        assertThat(schedules.detail(c.a(), id).title()).isEqualTo("한강 하프");

        assertThatThrownBy(
                        () ->
                                schedules.edit(
                                        c.a(),
                                        id,
                                        new CrewRunEdit("남의 글", "", null, CrewRunRepeat.NONE)))
                .isInstanceOf(PermissionDeniedException.class);
        posts.delete(c.l(), id);
        assertThatThrownBy(
                        () ->
                                schedules.edit(
                                        c.l(),
                                        id,
                                        new CrewRunEdit("지운 글", "", null, CrewRunRepeat.NONE)))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.POST_NOT_FOUND));
    }

    @Test
    @DisplayName("반복(D-성-26): 그 주 일요일 오전 9시에 +7일 회차 하나를 만들고(멱등), 바꾼 제목이 복사된다")
    void weeklyGeneration() {
        Crew3 c = crew();
        LocalDateTime at = tomorrow();
        long first = write(c.l(), c.id(), at, CrewRunRepeat.WEEKLY);
        CrewRunDetailView v = schedules.detail(c.l(), first);
        assertThat(v.repeat()).isEqualTo(CrewRunRepeat.WEEKLY);
        assertThat(v.repeatDayOfWeek()).isEqualTo(at.getDayOfWeek());

        LocalDateTime sunday = sundayOf(at);
        repeater.generateDue(sunday.minusWeeks(1));
        assertThat(postsOfCrew(c.id())).as("+7일이 다음다음 주").isEqualTo(1);

        schedules.edit(c.l(), first, new CrewRunEdit("바뀐 제목", "", null, CrewRunRepeat.WEEKLY));
        assertThat(repeater.generateDue(sunday)).isEqualTo(1);
        assertThat(repeater.generateDue(sunday.plusMinutes(1))).isZero();
        assertThat(postsOfCrew(c.id())).as("한 회차만").isEqualTo(2);

        long second =
                jdbc.queryForObject(
                        "select max(id) from crew_post where crew_id = ?", Long.class, c.id());
        CrewRunDetailView next = schedules.detail(c.a(), second);
        assertThat(next.scheduledAt()).isEqualTo(at.plusDays(7));
        assertThat(next.title()).isEqualTo("바뀐 제목");
        assertThat(next.distance()).isNull();
        assertThat(next.repeat()).isEqualTo(CrewRunRepeat.WEEKLY);
        assertThat(next.attendees())
                .extracting(CrewRunDetailView.Attendee::memberId)
                .containsExactly(c.l());
        assertThat(schedules.detail(c.l(), first).repeat())
                .as("다가오는 회차는 모두 매주 배지")
                .isEqualTo(CrewRunRepeat.WEEKLY);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from notification where recipient_id = ?"
                                        + " and type = 'CREW_RUN_RECRUIT'",
                                Integer.class,
                                c.a()))
                .as("새 회차도 모집 알림")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("반복 끝: PATCH NONE·최신 회차 삭제·작성자 탈퇴면 다음 회차를 만들지 않는다, PATCH WEEKLY는 새로 시작")
    void seriesEnds() {
        Crew3 c = crew();
        LocalDateTime at = tomorrow();

        long byPatch = write(c.l(), c.id(), at, CrewRunRepeat.WEEKLY);
        schedules.edit(c.l(), byPatch, new CrewRunEdit("그만", "", null, CrewRunRepeat.NONE));
        assertThat(schedules.detail(c.l(), byPatch).repeat()).isEqualTo(CrewRunRepeat.NONE);

        long restarted = write(c.a(), c.id(), at.plusHours(2), CrewRunRepeat.NONE);
        schedules.edit(c.a(), restarted, new CrewRunEdit("다시", "", null, CrewRunRepeat.WEEKLY));
        assertThat(schedules.detail(c.a(), restarted).repeatDayOfWeek())
                .isEqualTo(at.plusHours(2).getDayOfWeek());
        membership.leave(c.a(), c.id());

        long byDelete = write(c.b(), c.id(), at.plusHours(4), CrewRunRepeat.WEEKLY);
        posts.delete(c.l(), byDelete);
        assertThat(repeater.generateDue(sundayOf(at.plusHours(4))))
                .as("수정으로 끝낸 반복·작성자가 떠난 반복·최신 회차를 지운 반복")
                .isZero();
        assertThat(postsOfCrew(c.id())).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_run_series where crew_id = ?"
                                        + " and active = true",
                                Integer.class,
                                c.id()))
                .isZero();
    }

    @Test
    @DisplayName("지난 크루런: 그 달 FINISHED·CANCELLED만 최신순, 날짜 목록·하루 거르기·가장 이른 월")
    void pastByMonth() {
        Crew3 c = crew();
        long finished = write(c.l(), c.id(), tomorrow(), CrewRunRepeat.NONE);
        long cancelled = write(c.l(), c.id(), tomorrow(), CrewRunRepeat.NONE);
        long september = write(c.l(), c.id(), tomorrow(), CrewRunRepeat.NONE);
        long upcoming = write(c.l(), c.id(), tomorrow(), CrewRunRepeat.NONE);
        past(finished, "FINISHED", LocalDateTime.of(2026, 10, 20, 19, 0));
        past(cancelled, "CANCELLED", LocalDateTime.of(2026, 10, 13, 19, 0));
        past(september, "FINISHED", LocalDateTime.of(2026, 9, 30, 23, 55));

        PastCrewRunsView october = schedules.past(c.a(), c.id(), YearMonth.of(2026, 10), null);
        assertThat(october.dates())
                .containsExactly(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 20));
        assertThat(october.runs())
                .extracting(PastCrewRunsView.Run::postId)
                .containsExactly(finished, cancelled)
                .doesNotContain(upcoming);
        assertThat(october.runs().get(1).status()).isEqualTo(CrewRunStatus.CANCELLED);
        assertThat(october.runs().get(0).title()).isEqualTo("토요일 한강");
        assertThat(october.earliestMonth()).isEqualTo(YearMonth.of(2026, 9));

        PastCrewRunsView oneDay =
                schedules.past(c.a(), c.id(), YearMonth.of(2026, 10), LocalDate.of(2026, 10, 13));
        assertThat(oneDay.dates()).hasSize(2);
        assertThat(oneDay.runs())
                .extracting(PastCrewRunsView.Run::postId)
                .containsExactly(cancelled);

        assertThat(schedules.past(c.a(), c.id(), YearMonth.of(2026, 11), null).runs()).isEmpty();
        assertThatThrownBy(
                        () ->
                                schedules.past(
                                        c.a(),
                                        c.id(),
                                        YearMonth.of(2026, 10),
                                        LocalDate.of(2026, 11, 1)))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.INVALID_YEAR_MONTH));
        assertThatThrownBy(() -> schedules.past(c.o(), c.id(), YearMonth.of(2026, 10), null))
                .isInstanceOf(PermissionDeniedException.class);
    }

    private void past(long postId, String status, LocalDateTime at) {
        jdbc.update(
                "update crew_post set status = ?, scheduled_at = ? where id = ?",
                status,
                Timestamp.valueOf(at),
                postId);
    }
}
