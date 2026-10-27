package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.crew.AttendanceAnswer;
import com.a207.runkk.crew.BoardDraft;
import com.a207.runkk.crew.CrewCreateCommand;
import com.a207.runkk.crew.CrewPostService;
import com.a207.runkk.crew.CrewService;
import com.a207.runkk.crew.CrewVisibility;
import com.a207.runkk.crew.JoinPolicy;
import com.a207.runkk.crew.JoinTarget;
import com.a207.runkk.crew.JpegSamples;
import com.a207.runkk.crew.MeetupDraft;
import com.a207.runkk.crew.MembershipService;
import com.a207.runkk.crew.NoticeDraft;
import com.a207.runkk.crew.PhotoUpload;
import com.a207.runkk.crew.WeeklyRaceService;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 3장 통합 — S3a 스레드(실 PostgreSQL, Flyway V22 + ddl validate): 운영진 2명 상한 동시 지정(TC-6803), 고정 공지 동시
 * 고정(TC-6810), 대면 모임 정원 동시 참석(TC-6815), 공동 목표 동시 달성(T-29 TC-6742), 사진 BYTEA 저장·조회(TC-6817). 5.3 동시성
 * 행.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("크루 v0.11 스레드 통합(S3a, PostgreSQL)")
class CrewV011ThreadIntegrationTest {

    private static final int ROUNDS = 20;
    private static final AtomicInteger NAMES = new AtomicInteger();

    @Autowired CrewService crews;
    @Autowired MembershipService membership;
    @Autowired CrewPostService posts;
    @Autowired WeeklyRaceService race;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private long member() {
        String name = "동시러너" + NAMES.incrementAndGet();
        return tx.execute(
                s ->
                        members.save(MemberFixtures.member(name, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    /** 크루장과 크루원 n명. [0]은 크루 ID, [1]은 크루장, 나머지는 크루원. */
    private long[] crew(int memberCount) {
        long leader = member();
        long crewId =
                crews.create(
                                leader,
                                new CrewCreateCommand(
                                        "동시크루" + NAMES.incrementAndGet(),
                                        null,
                                        Set.of(PersonalityTag.SOCIAL_RUN),
                                        CrewVisibility.PUBLIC,
                                        JoinPolicy.FREE,
                                        null))
                        .crewId();
        long[] ids = new long[memberCount + 2];
        ids[0] = crewId;
        ids[1] = leader;
        for (int i = 0; i < memberCount; i++) {
            ids[i + 2] = member();
            membership.apply(ids[i + 2], JoinTarget.byCrew(crewId));
        }
        return ids;
    }

    private static <T> List<Object> together(List<Callable<T>> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(work.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> w : work) {
            futures.add(
                    pool.submit(
                            () -> {
                                start.await();
                                return w.call();
                            }));
        }
        start.countDown();
        pool.shutdown();
        List<Object> results = new ArrayList<>();
        for (Future<T> f : futures) {
            try {
                results.add(f.get());
            } catch (ExecutionException e) {
                results.add(e.getCause());
            }
        }
        return results;
    }

    private static long rejected(List<Object> results, ReasonCode reason) {
        return results.stream()
                .filter(r -> r instanceof BusinessRuleException e && e.reason() == reason)
                .count();
    }

    @Test
    @DisplayName("TC-6803: 운영진 1명일 때 두 지정이 동시에 오면 한쪽만 성공하고 운영진은 정확히 2명(crew 행 잠금)")
    void concurrentAppoint_neverExceedsTwo() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long[] c = crew(3);
            crews.appointManager(c[1], c[0], c[2]);
            List<Object> results =
                    together(
                            List.<Callable<Object>>of(
                                    () -> crews.appointManager(c[1], c[0], c[3]),
                                    () -> crews.appointManager(c[1], c[0], c[4])));
            assertThat(rejected(results, ReasonCode.MANAGER_LIMIT)).isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from crew_membership where crew_id = ?"
                                            + " and role = 'MANAGER' and left_at is null",
                                    Integer.class,
                                    c[0]))
                    .isEqualTo(2);
        }
    }

    @Test
    @DisplayName("TC-6810: 고정 2개일 때 공지 둘을 동시에 고정하면 한쪽만 성공하고 고정은 3개")
    void concurrentPins_neverExceedThree() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long[] c = crew(1);
            posts.writeNotice(c[1], c[0], new NoticeDraft("고정1", true));
            posts.writeNotice(c[1], c[0], new NoticeDraft("고정2", true));
            long a = posts.writeNotice(c[1], c[0], new NoticeDraft("가", false)).postId();
            long b = posts.writeNotice(c[1], c[0], new NoticeDraft("나", false)).postId();
            List<Object> results =
                    together(
                            List.<Callable<Object>>of(
                                    () -> posts.pin(c[1], a, true),
                                    () -> posts.pin(c[1], b, true)));
            assertThat(rejected(results, ReasonCode.PIN_LIMIT)).isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from crew_post where crew_id = ? and pinned",
                                    Integer.class,
                                    c[0]))
                    .isEqualTo(3);
        }
    }

    @Test
    @DisplayName("TC-6815: 정원 3(작성자 포함 GOING 2)에 두 크루원이 동시에 GOING이면 한쪽만 받는다")
    void concurrentMeetupAnswers_respectCapacity() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long[] c = crew(3);
            long meet =
                    posts.writeMeetup(
                                    c[2],
                                    c[0],
                                    new MeetupDraft(null, LocalDateTime.now().plusDays(1), "공원", 3))
                            .postId();
            posts.answerAttendance(c[1], meet, AttendanceAnswer.GOING);
            List<Object> results =
                    together(
                            List.<Callable<Object>>of(
                                    () ->
                                            posts.answerAttendance(
                                                    c[3], meet, AttendanceAnswer.GOING),
                                    () ->
                                            posts.answerAttendance(
                                                    c[4], meet, AttendanceAnswer.GOING)));
            assertThat(rejected(results, ReasonCode.SCHEDULE_FULL))
                    .as(results.toString())
                    .isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from crew_meetup_attendance where post_id = ?"
                                            + " and answer = 'GOING'",
                                    Integer.class,
                                    meet))
                    .isEqualTo(3);
        }
    }

    private RunRecordSaved saved(long memberId, long meters) {
        LocalDateTime start = LocalDateTime.now();
        return new RunRecordSaved(
                UUID.randomUUID().toString(),
                memberId,
                start,
                Distance.ofMeters(meters),
                Duration.ofMinutes(10),
                true,
                null,
                RunMode.FREE,
                null,
                null,
                true,
                1,
                start);
    }

    @Test
    @DisplayName("T-29 TC-6742: 두 기록이 함께 목표를 넘겨도 achieved_at은 한 번, CREW_GOAL_ACHIEVED는 크루원마다 1건")
    void concurrentGoalAchievement_isRecordedOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long[] c = crew(4);
            race.onRunSaved(saved(c[2], 29_000));
            race.setGoal(c[1], c[0], Distance.ofMeters(30_000), LocalDateTime.now());
            together(
                    List.<Callable<Object>>of(
                            () -> {
                                race.onRunSaved(saved(c[3], 1_000));
                                return null;
                            },
                            () -> {
                                race.onRunSaved(saved(c[4], 2_000));
                                return null;
                            }));
            assertThat(
                            jdbc.queryForObject(
                                    "select total_meters from weekly_race where crew_id = ?",
                                    Long.class,
                                    c[0]))
                    .isEqualTo(32_000);
            for (int i = 1; i < c.length; i++) {
                assertThat(
                                jdbc.queryForObject(
                                        "select count(*) from notification where recipient_id = ?"
                                                + " and type = 'CREW_GOAL_ACHIEVED'",
                                        Integer.class,
                                        c[i]))
                        .isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("TC-6817: 사진 바이트는 BYTEA에 위치 메타데이터 없이 저장되고 그대로 읽힌다")
    void photoBytesRoundTrip() {
        long[] c = crew(1);
        byte[] gps = JpegSamples.withLocation(new byte[2048]);
        long post =
                posts.writeBoard(c[2], c[0], new BoardDraft("사진", List.of(new PhotoUpload(gps))))
                        .postId();
        long photoId =
                jdbc.queryForObject(
                        "select id from crew_photo where post_id = ?", Long.class, post);
        byte[] stored = posts.photo(c[1], photoId).jpeg();
        assertThat(stored).isEqualTo(JpegSamples.clean(new byte[2048]));
        assertThat(
                        jdbc.queryForObject(
                                "select size_bytes from crew_photo where id = ?",
                                Integer.class,
                                photoId))
                .isEqualTo(stored.length);
    }
}
