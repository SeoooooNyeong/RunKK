package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * crew 응용 서비스 흐름(14장 통합 — 사용 기반 crew 단계, test 프로필 H2 + 내장 Redis): 탈퇴 정리(D-28, FR-021), 크루장 탈퇴
 * 가드(FR-024), 공개 조회 클래스(CrewMembershipReader·CrewRunParticipationReader), 크루런 취소·그만두기·결과, 가입 신청
 * 만료(Q-40).
 */
@SpringBootTest(classes = RunkkApplication.class)
@ActiveProfiles("test")
@org.junit.jupiter.api.DisplayName("crew 응용 서비스 흐름")
class CrewScenarioTest {

    private static final AtomicInteger NAMES = new AtomicInteger();

    @Autowired CrewService crews;
    @Autowired MembershipService membership;
    @Autowired CrewPostService posts;
    @Autowired CrewRunService crewRuns;
    @Autowired CrewRunLiveService live;
    @Autowired CrewLiveSessionStore sessions;
    @Autowired WeeklyRaceService race;
    @Autowired CrewWithdrawalListener withdrawal;
    @Autowired CrewLeaderGuard leaderGuard;
    @Autowired CrewMembershipReader reader;
    @Autowired CrewRunParticipationReader participationReader;
    @Autowired CrewQueryService queries;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;

    private long member() {
        String name = "흐름러너" + NAMES.incrementAndGet();
        return tx.execute(s -> members.save(MemberFixtures.member(name)).getId());
    }

    private long crew(long leader, JoinPolicy policy) {
        return crews.create(
                        leader,
                        new CrewCreateCommand(
                                "흐름크루" + NAMES.incrementAndGet(),
                                null,
                                Set.of(PersonalityTag.SOCIAL_RUN),
                                CrewVisibility.PUBLIC,
                                policy,
                                "https://cdn/logo.png"))
                .crewId();
    }

    private static RunRecordSaved crewRunSaved(long memberId, long postId, String recordId) {
        LocalDateTime start = LocalDateTime.now();
        return new RunRecordSaved(
                recordId,
                memberId,
                start,
                Distance.ofMeters(500),
                Duration.ofMinutes(3),
                false,
                null,
                RunMode.CREW_RUN,
                null,
                postId,
                true,
                0,
                start);
    }

    @Test
    void publicReadersAndLeaderGuard() {
        long leader = member();
        long a = member();
        long crewId = crew(leader, JoinPolicy.FREE);
        membership.apply(a, JoinTarget.byCrew(crewId));

        assertThat(reader.activeCrewOf(a))
                .hasValueSatisfying(
                        c -> {
                            assertThat(c.crewId()).isEqualTo(crewId);
                            assertThat(c.logoUrl()).isEqualTo("https://cdn/logo.png");
                        });
        assertThat(reader.sameCrew(leader, a)).isTrue();
        assertThat(reader.sameCrew(leader, member())).isFalse();
        assertThat(reader.activeMemberIds(crewId)).containsExactlyInAnyOrder(leader, a);
        assertThat(reader.activeMembersByCrew().get(crewId)).containsExactlyInAnyOrder(leader, a);
        assertThat(reader.crewNames(List.of(crewId, -1L))).containsOnlyKeys(crewId);
        assertThat(leaderGuard.check(leader)).contains(ReasonCode.LEADER_MUST_TRANSFER);
        assertThat(leaderGuard.check(a)).isEmpty();
    }

    @Test
    void withdrawal_removesMembershipsAnonymizesAndDeletesLonelyCrews() {
        long leader = member();
        long a = member();
        long crewId = crew(leader, JoinPolicy.FREE);
        membership.apply(a, JoinTarget.byCrew(crewId));
        long post = posts.writeCheer(a, crewId, "남길 응원").postId();
        long lonelyLeader = member();
        long lonely = crew(lonelyLeader, JoinPolicy.FREE);

        withdrawal.onMemberWithdrawn(new MemberWithdrawn(a, LocalDateTime.now()));
        withdrawal.onMemberWithdrawn(new MemberWithdrawn(lonelyLeader, LocalDateTime.now()));

        assertThat(reader.activeMemberIds(crewId)).containsExactly(leader);
        PostListView list = posts.list(leader, crewId, PostKind.CHEER);
        assertThat(list.posts())
                .singleElement()
                .satisfies(
                        l -> {
                            assertThat(l.summary().postId()).isEqualTo(post);
                            assertThat(l.authorName()).isEqualTo(DisplayName.WITHDRAWN);
                        });
        assertThat(reader.activeMemberIds(lonely)).isEmpty();
        assertThatThrownBy(() -> queries.members(leader, lonely))
                .satisfies(
                        e ->
                                assertThat(((BusinessRuleException) e).reason())
                                        .isEqualTo(ReasonCode.CREW_NOT_FOUND));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "L17: 가드 검사 뒤 크루원이 들어와 크루장이 크루원과 함께 탈퇴되면 가장 먼저 들어온 크루원이 크루장을 넘겨받는다")
    void leaderWithdrawnWithMembers_handsOverToEarliestMember() {
        long leader = member();
        long first = member();
        long second = member();
        long crewId = crew(leader, JoinPolicy.FREE);
        membership.apply(first, JoinTarget.byCrew(crewId));
        membership.apply(second, JoinTarget.byCrew(crewId));

        withdrawal.onMemberWithdrawn(new MemberWithdrawn(leader, LocalDateTime.now()));

        assertThat(reader.activeMemberIds(crewId)).containsExactlyInAnyOrder(first, second);
        assertThat(leaderGuard.check(first)).contains(ReasonCode.LEADER_MUST_TRANSFER);
        assertThat(leaderGuard.check(second)).isEmpty();
    }

    @Test
    void crewRun_cancelledWhenFewerThanTwoReady_andQuitDuringRun() {
        long leader = member();
        long a = member();
        long crewId = crew(leader, JoinPolicy.FREE);
        membership.apply(a, JoinTarget.byCrew(crewId));
        LocalDateTime at = LocalDateTime.now().plusMinutes(20).withNano(0);

        long lonelyRun =
                posts.writeCrewRun(
                                leader,
                                crewId,
                                new CrewRunDraft("혼자", at, StandardDistance.KM3, null))
                        .postId();
        crewRuns.enterLobby(leader, lonelyRun);
        crewRuns.toggleReady(leader, lonelyRun);
        crewRuns.tick(at);
        assertThat(crewRuns.result(leader, lonelyRun).status()).isEqualTo(CrewRunStatus.CANCELLED);

        long run =
                posts.writeCrewRun(
                                leader,
                                crewId,
                                new CrewRunDraft("둘이", at, StandardDistance.KM1, null))
                        .postId();
        for (long m : List.of(leader, a)) {
            crewRuns.enterLobby(m, run);
            crewRuns.toggleReady(m, run);
        }
        crewRuns.tick(at);
        sessions.delete(run);
        sessions.save(CrewRunLiveSession.open(-77L, StandardDistance.KM1, java.util.Map.of(), at));
        crewRuns.tick(at.plusSeconds(10));
        assertThat(sessions.load(run)).as("Redis에서 빠진 세션을 시계가 되살린다").isPresent();
        assertThat(sessions.load(-77L)).as("시계가 보지 않는 크루런 세션은 지운다").isEmpty();
        live.onProgress(
                a,
                run,
                new ProgressReport(Distance.ofMeters(200), Duration.ofSeconds(60), at),
                at.plusSeconds(63));
        crewRuns.quit(a, run);
        assertThat(sessions.load(run).orElseThrow().ranking())
                .filteredOn(e -> e.connection() == RelayEntry.Connection.QUIT)
                .hasSize(1);
        live.detectTimeouts(at.plusSeconds(70));

        crewRuns.onRunSaved(crewRunSaved(a, run, "quit-record-000000000000000000000001"));
        assertThat(
                        participationReader.linkedRecordIds(
                                a, new DateRange(LocalDate.now(), at.toLocalDate())))
                .containsExactly("quit-record-000000000000000000000001");
        crewRuns.tick(at.plusMinutes(16));
        CrewRunResultView result = crewRuns.result(a, run);
        assertThat(result.status()).isEqualTo(CrewRunStatus.FINISHED);
        assertThat(result.finisherCount()).isZero();
        assertThat(result.runners()).filteredOn(CrewRunResultView.Line::quit).hasSize(1);
        assertThat(sessions.load(run)).isEmpty();
        assertThat(race.contributionOf("quit-record-000000000000000000000001"))
                .isEqualTo(Distance.ZERO);
    }

    @Test
    void pendingRequests_expireAfterSevenDays() {
        long leader = member();
        long a = member();
        long crewId = crew(leader, JoinPolicy.APPROVAL);
        long request = membership.apply(a, JoinTarget.byCrew(crewId)).requestId();

        assertThat(membership.expireRequests(LocalDateTime.now().plusDays(6))).isZero();
        assertThat(membership.expireRequests(LocalDateTime.now().plusDays(8))).isEqualTo(1);
        assertThatThrownBy(() -> membership.approveJoin(leader, request))
                .satisfies(
                        e ->
                                assertThat(((BusinessRuleException) e).reason())
                                        .isEqualTo(ReasonCode.REQUEST_DECIDED));
        assertThat(queries.pendingRequests(leader, crewId).requests()).isEmpty();
    }
}
