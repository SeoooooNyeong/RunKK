package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.CrewTag;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunRecordDeleted;
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
                                Set.of(CrewTag.WEEKDAY_EVENING),
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
        PostListView list =
                posts.list(
                        leader,
                        crewId,
                        new PostQuery(Set.of(PostKind.CHEER), false, PageCursor.all()));
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
        sessions.save(CrewRunLiveSession.open(-77L, java.util.Map.of(), at));
        crewRuns.tick(at.plusSeconds(10));
        assertThat(sessions.load(run)).as("Redis에서 빠진 세션을 시계가 되살린다").isPresent();
        assertThat(sessions.load(-77L)).as("시계가 보지 않는 크루런 세션은 지운다").isEmpty();
        live.onProgress(
                a,
                run,
                new ProgressReport(Distance.ofMeters(200), Duration.ofSeconds(60), at),
                at.plusSeconds(63));
        crewRuns.pause(leader, run, Duration.ofSeconds(60));
        assertThat(sessions.load(run).orElseThrow().entries())
                .filteredOn(e -> e.state() == RelayEntry.State.PAUSED)
                .extracting(RelayEntry::memberId)
                .containsExactly(leader);
        crewRuns.quit(a, run, null);
        crewRuns.quit(a, run, null);
        assertThat(sessions.load(run).orElseThrow().entries())
                .filteredOn(e -> e.state() == RelayEntry.State.ENDED)
                .extracting(RelayEntry::memberId)
                .containsExactly(a);
        live.detectTimeouts(at.plusSeconds(70));

        crewRuns.onRunSaved(crewRunSaved(a, run, "quit-record-000000000000000000000001"));
        assertThat(
                        participationReader.linkedRecordIds(
                                a, new DateRange(LocalDate.now(), at.toLocalDate())))
                .containsExactly("quit-record-000000000000000000000001");
        crewRuns.tick(at.plusMinutes(16));
        CrewRunResultView result = crewRuns.result(a, run);
        assertThat(result.status()).isEqualTo(CrewRunStatus.FINISHED);
        assertThat(result.participants())
                .extracting(CrewRunResultView.Line::memberId)
                .containsExactly(leader, a);
        assertThat(result.participants()).allMatch(CrewRunResultView.Line::ended);
        CrewRunResultView.Line mine = result.participants().get(1);
        assertThat(mine.distance()).as("기록 요약이 없으면 끝낼 때 남긴 중계 값").isEqualTo(Distance.ofMeters(200));
        assertThat(mine.elapsed()).isEqualTo(Duration.ofSeconds(60));
        assertThat(mine.averagePace().secondsPerKm()).isEqualTo(300);
        assertThat(result.participants().get(0).distance()).isEqualTo(Distance.ZERO);
        assertThat(result.totalDistance()).isEqualTo(Distance.ofMeters(200));
        assertThat(result.myRecordId()).isEqualTo("quit-record-000000000000000000000001");
        assertThat(result.title()).isEqualTo("둘이");
        assertThat(result.startedAt()).isEqualTo(at.plusSeconds(3));
        assertThat(sessions.load(run)).isEmpty();
        assertThat(race.contributionOf("quit-record-000000000000000000000001"))
                .isEqualTo(Distance.ZERO);

        // TC-9357 기록 삭제 사건: 크루런 ID가 없으면 아무 일도 없고, 있으면 그 참가의 연결을 비운다
        DateRange linkedRange = new DateRange(LocalDate.now(), at.toLocalDate());
        crewRuns.onRunDeleted(
                new RunRecordDeleted(
                        "quit-record-000000000000000000000001",
                        a,
                        null,
                        null,
                        LocalDateTime.now()));
        assertThat(participationReader.linkedRecordIds(a, linkedRange))
                .containsExactly("quit-record-000000000000000000000001");
        crewRuns.onRunDeleted(
                new RunRecordDeleted(
                        "quit-record-000000000000000000000001", a, null, run, LocalDateTime.now()));
        assertThat(participationReader.linkedRecordIds(a, linkedRange)).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "BR-2: 세션을 다시 열면 이미 끝낸 참가자는 DB의 마감 거리·경과로 ENDED, 합산 거리에 들어간다")
    void ensureSession_restoresEndedRunnersFromParticipations() {
        long leader = member();
        long a = member();
        long crewId = crew(leader, JoinPolicy.FREE);
        membership.apply(a, JoinTarget.byCrew(crewId));
        LocalDateTime at = LocalDateTime.now().plusMinutes(20).withNano(0);
        long run =
                posts.writeCrewRun(
                                leader,
                                crewId,
                                new CrewRunDraft("다시 열기", at, StandardDistance.KM3, null))
                        .postId();
        for (long m : List.of(leader, a)) {
            crewRuns.enterLobby(m, run);
            crewRuns.toggleReady(m, run);
        }
        crewRuns.tick(at);
        crewRuns.quit(a, run, new FinalProgress(Distance.ofMeters(1_200), Duration.ofSeconds(420)));

        sessions.delete(run);
        crewRuns.tick(at.plusSeconds(10));
        live.detectTimeouts(at.plusSeconds(20));

        CrewRunLiveSession reopened = sessions.load(run).orElseThrow();
        assertThat(reopened.entries())
                .filteredOn(e -> e.memberId() == a)
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.state()).isEqualTo(RelayEntry.State.ENDED);
                            assertThat(e.distance()).isEqualTo(Distance.ofMeters(1_200));
                        });
        assertThat(reopened.totalDistance()).isEqualTo(Distance.ofMeters(1_200));
        assertThat(reopened.latestOf(a).map(LiveProgress::elapsed))
                .contains(Duration.ofSeconds(420));
        assertThat(reopened.entries())
                .filteredOn(e -> e.memberId() == leader)
                .extracting(RelayEntry::state)
                .as("아직 달리는 사람은 진행값이 없어 연결 확인 중")
                .containsExactly(RelayEntry.State.CHECKING);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("AN-3: 진행 중 결과는 아직 달리는 참가자의 거리·경과를 0이 아니라 마지막 중계 값으로 준다")
    void runningResult_usesLatestRelayForRunnersStillRunning() {
        long leader = member();
        long a = member();
        long crewId = crew(leader, JoinPolicy.FREE);
        membership.apply(a, JoinTarget.byCrew(crewId));
        LocalDateTime at = LocalDateTime.now().plusMinutes(20).withNano(0);
        long run =
                posts.writeCrewRun(
                                leader,
                                crewId,
                                new CrewRunDraft("달리는 중", at, StandardDistance.KM3, null))
                        .postId();
        for (long m : List.of(leader, a)) {
            crewRuns.enterLobby(m, run);
            crewRuns.toggleReady(m, run);
        }
        crewRuns.tick(at);
        crewRuns.quit(a, run, new FinalProgress(Distance.ofMeters(1_000), Duration.ofSeconds(330)));
        live.onProgress(
                leader,
                run,
                new ProgressReport(Distance.ofMeters(1_500), Duration.ofSeconds(480), at),
                at.plusSeconds(483));

        CrewRunResultView result = crewRuns.result(a, run);

        assertThat(result.status()).isEqualTo(CrewRunStatus.RUNNING);
        CrewRunResultView.Line running =
                result.participants().stream()
                        .filter(l -> Long.valueOf(leader).equals(l.memberId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(running.ended()).isFalse();
        assertThat(running.distance()).isEqualTo(Distance.ofMeters(1_500));
        assertThat(running.elapsed()).isEqualTo(Duration.ofSeconds(480));
        assertThat(running.averagePace().secondsPerKm()).isEqualTo(320);
        assertThat(result.totalDistance()).isEqualTo(Distance.ofMeters(2_500));

        crewRuns.tick(at.plusMinutes(46));
        CrewRunResultView finished = crewRuns.result(a, run);
        assertThat(finished.status()).isEqualTo(CrewRunStatus.FINISHED);
        assertThat(finished.totalDistance())
                .as("종료 때 마지막 중계 값으로 마감한 값과 같다")
                .isEqualTo(Distance.ofMeters(2_500));
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
