package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.CrewTag;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Week;
import com.a207.runkk.crew.CrewCreateCommand;
import com.a207.runkk.crew.CrewMembersView;
import com.a207.runkk.crew.CrewQueryService;
import com.a207.runkk.crew.CrewService;
import com.a207.runkk.crew.CrewVisibility;
import com.a207.runkk.crew.GoalHistoryView;
import com.a207.runkk.crew.JoinPolicy;
import com.a207.runkk.crew.JoinTarget;
import com.a207.runkk.crew.MembershipService;
import com.a207.runkk.crew.WeeklyRaceService;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 * W7(contract-W7.md) 조회 SQL을 실 PostgreSQL(Flyway + ddl validate)에서 확인한다: 크루원별 누적 기여 거리 집계와 지난 목표
 * 목록(주 커서, 참여 인원 부분 집계).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("크루 W7 조회 통합(PostgreSQL)")
class CrewW7QueryIntegrationTest {

    private static final AtomicInteger NAMES = new AtomicInteger();

    @Autowired CrewService crews;
    @Autowired MembershipService membership;
    @Autowired CrewQueryService queries;
    @Autowired WeeklyRaceService race;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private long member() {
        String name = "W7러너" + NAMES.incrementAndGet();
        return tx.execute(
                s ->
                        members.save(MemberFixtures.member(name, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    private long crew(long leader) {
        return crews.create(
                        leader,
                        new CrewCreateCommand(
                                "W7크루" + NAMES.incrementAndGet(),
                                null,
                                Set.of(CrewTag.WEEKDAY_EVENING),
                                CrewVisibility.PUBLIC,
                                JoinPolicy.FREE,
                                null))
                .crewId();
    }

    private void contribute(long crewId, LocalDate week, Long memberId, long meters) {
        jdbc.update(
                "insert into race_contribution (crew_id, week_start, member_id, record_id, meters)"
                        + " values (?, ?, ?, ?, ?)",
                crewId,
                week,
                memberId,
                UUID.randomUUID().toString(),
                meters);
    }

    @Test
    @DisplayName("크루원 누적 거리는 이 크루 기여만 합하고, 지난 목표는 목표 있는 지난 주만 최신순·참여 인원과 함께")
    void accumulatedAndGoalHistory_onPostgres() {
        long leader = member();
        long a = member();
        long crewId = crew(leader);
        membership.apply(a, JoinTarget.byCrew(crewId));
        LocalDate monday = Week.of(LocalDateTime.now(clock)).monday();
        LocalDate w1 = monday.minusWeeks(1);
        LocalDate w2 = monday.minusWeeks(2);
        contribute(crewId, w1, a, 5_000);
        contribute(crewId, w2, a, 2_500);
        contribute(crewId, w1, leader, 1_000);
        contribute(crewId, w1, null, 700);
        contribute(crewId + 100_000, w1, a, 99_000);
        jdbc.update(
                "insert into weekly_race (crew_id, week_start, total_meters, goal_meters,"
                        + " achieved_at) values (?, ?, ?, ?, ?), (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)",
                crewId,
                monday,
                0,
                1_000,
                null,
                crewId,
                w1,
                6_700,
                5_000,
                w1.atTime(9, 0),
                crewId,
                w2,
                2_500,
                null,
                null);

        CrewMembersView view = queries.members(a, crewId);
        assertThat(view.members())
                .extracting(CrewMembersView.Line::memberId)
                .containsExactly(a, leader);
        assertThat(view.members().get(0).accumulated()).isEqualTo(Distance.ofMeters(7_500));
        assertThat(view.members().get(1).accumulated()).isEqualTo(Distance.ofMeters(1_000));
        assertThat(view.members().get(0).joinedAt()).isNotNull();

        GoalHistoryView history = race.goalHistory(a, crewId, Optional.empty(), 20);
        assertThat(history.goals()).extracting(GoalHistoryView.Line::weekStart).containsExactly(w1);
        GoalHistoryView.Line line = history.goals().get(0);
        assertThat(line.achieved()).isTrue();
        assertThat(line.progressPercent()).isEqualTo(134);
        assertThat(line.participantCount()).as("a·크루장·익명 한 묶음").isEqualTo(3);
        assertThat(history.nextBefore()).isNull();
    }
}
