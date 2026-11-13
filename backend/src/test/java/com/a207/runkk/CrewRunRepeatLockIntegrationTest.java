package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.CrewTag;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.crew.CrewCreateCommand;
import com.a207.runkk.crew.CrewPostService;
import com.a207.runkk.crew.CrewRunDraft;
import com.a207.runkk.crew.CrewRunRepeat;
import com.a207.runkk.crew.CrewRunRepeater;
import com.a207.runkk.crew.CrewService;
import com.a207.runkk.crew.CrewVisibility;
import com.a207.runkk.crew.JoinPolicy;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * 크루런 매주 반복의 잠금 순서(코드 리뷰 M1, contract-W6 3절): 회차 생성은 반복 → 최신 회차 글 순서로 잠근다. 글 삭제·크루 삭제가 글을 먼저 잠그고
 * 반복을 기다리면 교착이 생기므로, 두 경로 모두 반복을 먼저 잠가야 한다. 실 PostgreSQL에서 생성 쪽 잠금 순서를 한 트랜잭션으로 재현해 교착이 없는지, 실제 생성과
 * 삭제를 겹쳐 반복이 끝나는지 고정한다. Docker가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("크루런 반복 잠금 순서(PostgreSQL)")
class CrewRunRepeatLockIntegrationTest {

    private static final AtomicInteger NAMES = new AtomicInteger();

    @Autowired CrewService crews;
    @Autowired CrewPostService posts;
    @Autowired CrewRunRepeater repeater;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private record Series(long crewId, long leader, long postId, long seriesId, LocalDateTime at) {}

    private long member() {
        String name = "반복잠금" + NAMES.incrementAndGet();
        return tx.execute(
                s ->
                        members.save(MemberFixtures.member(name, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    /** 크루장 혼자인 크루에 내일 매주 반복 크루런 하나. */
    private Series weekly() {
        long leader = member();
        long crewId =
                crews.create(
                                leader,
                                new CrewCreateCommand(
                                        "반복잠금크루" + NAMES.incrementAndGet(),
                                        null,
                                        Set.of(CrewTag.WEEKDAY_EVENING),
                                        CrewVisibility.PUBLIC,
                                        JoinPolicy.FREE,
                                        null))
                        .crewId();
        LocalDateTime at = LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0);
        long postId =
                posts.writeCrewRun(
                                leader,
                                crewId,
                                new CrewRunDraft(
                                        "매주 한강",
                                        "",
                                        at,
                                        StandardDistance.KM5,
                                        null,
                                        CrewRunRepeat.WEEKLY))
                        .postId();
        long seriesId =
                jdbc.queryForObject(
                        "select series_id from crew_post where id = ?", Long.class, postId);
        return new Series(crewId, leader, postId, seriesId, at);
    }

    /** at이 든 주(월~일)의 일요일 9시: 반복 작업이 다음 주 회차를 만드는 시각. */
    private static LocalDateTime sundayOf(LocalDateTime at) {
        return at.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(9, 0);
    }

    private int lockWaiters() {
        return jdbc.queryForObject(
                "select count(*) from pg_locks where not granted", Integer.class);
    }

    /** 다른 연결이 행 잠금을 기다리기 시작할 때까지(최대 5초). */
    private void awaitLockWaiter() throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (lockWaiters() == 0 && System.nanoTime() < until) {
            Thread.sleep(20);
        }
    }

    /**
     * 회차 생성의 잠금 순서(반복 → 최신 회차 글)를 한 트랜잭션으로 재현한다: 반복을 잠근 채 경쟁 작업을 띄우고, 그 작업이 잠금을 기다리기 시작하면 최신 회차 글을
     * 잠근다. 경쟁 작업이 글을 먼저 잠갔다면 여기서 교착이 나 한쪽이 실패한다.
     */
    private <T> T underGeneratorLocks(Series s, Callable<T> rival) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<T> future =
                    tx.execute(
                            status -> {
                                jdbc.queryForList(
                                        "select id from crew_run_series where id = ? for update",
                                        s.seriesId());
                                Future<T> f = pool.submit(rival);
                                try {
                                    awaitLockWaiter();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                jdbc.queryForList(
                                        "select id from crew_post where id = ? for update",
                                        s.postId());
                                return f;
                            });
            return future.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean seriesActive(long seriesId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select active from crew_run_series where id = ?",
                        Boolean.class,
                        seriesId));
    }

    private int crewRunsOf(long crewId) {
        return jdbc.queryForObject(
                "select count(*) from crew_post where crew_id = ? and deleted = false",
                Integer.class,
                crewId);
    }

    @Test
    @DisplayName("생성이 반복을 잡고 있을 때 최신 회차 삭제는 글보다 반복을 먼저 기다린다(교착 없음) → 반복이 끝나 다음 회차를 만들지 않는다")
    void deleteWaitsForSeriesBeforePost() throws Exception {
        Series s = weekly();

        underGeneratorLocks(s, () -> posts.delete(s.leader(), s.postId()));

        assertThat(seriesActive(s.seriesId())).as("최신 회차를 지웠으니 반복이 끝난다").isFalse();
        assertThat(repeater.generateDue(sundayOf(s.at()))).isZero();
        assertThat(crewRunsOf(s.crewId())).isZero();
    }

    @Test
    @DisplayName("생성이 반복을 잡고 있을 때 크루 삭제도 글보다 반복을 먼저 기다린다(교착 없음) → 반복 행까지 지워진다")
    void crewDeletionWaitsForSeriesBeforePosts() throws Exception {
        Series s = weekly();

        underGeneratorLocks(s, () -> crews.delete(s.leader(), s.crewId()));

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_run_series where id = ?",
                                Integer.class,
                                s.seriesId()))
                .isZero();
        assertThat(repeater.generateDue(sundayOf(s.at()))).isZero();
    }

    @Test
    @DisplayName("실제 생성과 최신 회차 삭제를 겹쳐도 둘 다 실패 없이 끝나고, 결과는 순서대로 한 것과 같다")
    void realGenerateVersusDelete() throws Exception {
        for (int round = 0; round < 5; round++) {
            Series s = weekly();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> generated =
                        pool.submit(() -> repeater.generateDue(sundayOf(s.at())));
                Future<?> deleted = pool.submit(() -> posts.delete(s.leader(), s.postId()));
                deleted.get(30, TimeUnit.SECONDS);
                int made = generated.get(30, TimeUnit.SECONDS);

                if (made == 0) {
                    assertThat(seriesActive(s.seriesId())).as("삭제가 먼저: 반복이 끝난다").isFalse();
                    assertThat(crewRunsOf(s.crewId())).isZero();
                } else {
                    long latest =
                            jdbc.queryForObject(
                                    "select last_post_id from crew_run_series where id = ?",
                                    Long.class,
                                    s.seriesId());
                    assertThat(latest).as("생성이 먼저: 새 회차가 최신").isNotEqualTo(s.postId());
                    assertThat(crewRunsOf(s.crewId())).isEqualTo(1);
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
