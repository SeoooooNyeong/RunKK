package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.CrewTag;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.crew.live.CrewRunChanged;
import com.a207.runkk.crew.live.CrewRunChanged.Resource;
import com.a207.runkk.crew.live.CrewRunDeparture;
import com.a207.runkk.crew.live.CrewRunStart;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 크루런 실시간 반영 응용 흐름(설계 05-32, test 프로필 H2 + 내장 Redis): 커밋 뒤 현재 크루원에게만 가는 변경 신호, 출발 사실과 기존 시작 신호의 받는
 * 사람, 중간 참여의 세션 반영과 누락 보충, 롤백 신호 0, 세 출발 경로 경합의 예약 하나. 실시간 송신은 실제 빈을 감싼 spy로 받는 사람과 메시지를 본다.
 */
@SpringBootTest(classes = RunkkApplication.class)
@ActiveProfiles("test")
@org.junit.jupiter.api.DisplayName("크루런 실시간 반영 흐름(05-32)")
class CrewRunRealtimeScenarioTest {

    private static final AtomicInteger NAMES = new AtomicInteger();

    @MockitoSpyBean CrewRunMessenger messenger;
    @MockitoSpyBean CrewRunLiveService live;
    @Autowired CrewService crews;
    @Autowired MembershipService membership;
    @Autowired CrewPostService posts;
    @Autowired CrewScheduleService schedules;
    @Autowired CrewRunService crewRuns;
    @Autowired CrewLiveSessionStore sessions;
    @Autowired CrewPostRepository crewPostRepository;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;

    /** spy가 받은 sendAll 한 번. */
    private record Sent(List<Long> to, CrewRunMessage message) {}

    private final List<Sent> sent = java.util.Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void capture() {
        reset(messenger, live);
        sent.clear();
        Mockito.doAnswer(
                        inv -> {
                            Collection<Long> to = inv.getArgument(0);
                            sent.add(new Sent(List.copyOf(to), inv.getArgument(1)));
                            return inv.callRealMethod();
                        })
                .when(messenger)
                .sendAll(any(), any());
    }

    private long member() {
        String name = "실시간러너" + NAMES.incrementAndGet();
        return tx.execute(s -> members.save(MemberFixtures.member(name)).getId());
    }

    private long crew(long leader, long... others) {
        long crewId =
                crews.create(
                                leader,
                                new CrewCreateCommand(
                                        "실시간크루" + NAMES.incrementAndGet(),
                                        null,
                                        Set.of(CrewTag.WEEKDAY_EVENING),
                                        CrewVisibility.PUBLIC,
                                        JoinPolicy.FREE,
                                        "https://cdn/logo.png"))
                        .crewId();
        for (long m : others) {
            membership.apply(m, JoinTarget.byCrew(crewId));
        }
        return crewId;
    }

    private long crewRun(long author, long crewId, LocalDateTime at) {
        return posts.writeCrewRun(
                        author, crewId, new CrewRunDraft("실시간", at, StandardDistance.KM3, null))
                .postId();
    }

    private static LocalDateTime in20Minutes() {
        return LocalDateTime.now().plusMinutes(20).withNano(0);
    }

    private List<CrewRunChanged> changes(long postId, Resource resource) {
        return sent.stream()
                .filter(s -> s.message() instanceof CrewRunChanged c && c.postId() == postId)
                .map(s -> (CrewRunChanged) s.message())
                .filter(c -> c.resource() == resource)
                .toList();
    }

    private List<Long> recipientsOf(long postId, Resource resource) {
        return sent.stream()
                .filter(
                        s ->
                                s.message() instanceof CrewRunChanged c
                                        && c.postId() == postId
                                        && c.resource() == resource)
                .flatMap(s -> s.to().stream())
                .distinct()
                .toList();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "[임시:TC-성-11020·11041] 입장·준비·이탈은 커밋 뒤 LOBBY 신호를 현재 크루원 전원에게만 보내고, 순번은 오르기만 하며 eventId는"
                    + " crewId:postId:revision:resource. 비크루원은 읽기·준비·시작·참여가 막히고 신호를 받지 않는다")
    void lobbySignals_reachCurrentMembersOnly() {
        long leader = member();
        long a = member();
        long b = member();
        long outsider = member();
        long crewId = crew(leader, a, b);
        crew(outsider);
        long run = crewRun(leader, crewId, in20Minutes());

        crewRuns.enterLobby(a, run);
        crewRuns.setReady(a, run, true);
        crewRuns.leaveLobby(a, run);

        List<CrewRunChanged> lobby = changes(run, Resource.LOBBY);
        assertThat(lobby).hasSize(3);
        assertThat(lobby).extracting(CrewRunChanged::crewId).containsOnly(crewId);
        assertThat(lobby)
                .extracting(CrewRunChanged::revision)
                .as("순번은 커지기만 한다")
                .isSorted()
                .doesNotHaveDuplicates();
        assertThat(lobby.get(0).eventId())
                .isEqualTo(crewId + ":" + run + ":" + lobby.get(0).revision() + ":LOBBY");
        assertThat(recipientsOf(run, Resource.LOBBY))
                .containsExactlyInAnyOrder(leader, a, b)
                .doesNotContain(outsider);
        assertThat(changes(run, Resource.SCHEDULE)).as("모집과 입장은 목록 신호").hasSize(2);

        for (Runnable blocked :
                List.<Runnable>of(
                        () -> crewRuns.readLobby(outsider, run),
                        () -> crewRuns.setReady(outsider, run, true),
                        () -> crewRuns.startNow(outsider, run),
                        () -> crewRuns.join(outsider, run))) {
            assertThatThrownBy(blocked::run).isInstanceOf(PermissionDeniedException.class);
        }
        // 탈퇴하면 다음 신호부터 받지 않는다(발송 시점 소속)
        membership.leave(b, crewId);
        sent.clear();
        crewRuns.enterLobby(a, run);
        assertThat(recipientsOf(run, Resource.LOBBY)).containsExactlyInAnyOrder(leader, a);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "[임시:TC-성-11019] 대기실 GET은 준비·순번·JPA version을 바꾸지 않고 신호도 보내지 않는다")
    void readLobby_isReadOnly() {
        long leader = member();
        long a = member();
        long crewId = crew(leader, a);
        long run = crewRun(leader, crewId, in20Minutes());
        crewRuns.enterLobby(leader, run);
        crewRuns.enterLobby(a, run);
        crewRuns.setReady(a, run, true);
        Long version = versionOf(run);
        long revision = crewRuns.readLobby(a, run).revision();
        sent.clear();
        for (int i = 0; i < 10; i++) {
            LobbyView view = crewRuns.readLobby(a, run);
            assertThat(view.revision()).isEqualTo(revision);
            assertThat(view.participants())
                    .filteredOn(e -> e.memberId() == a)
                    .extracting(LobbyView.Entry::readiness)
                    .containsExactly(Readiness.READY);
        }
        assertThat(versionOf(run)).isEqualTo(version);
        assertThat(sent).isEmpty();
    }

    private Long versionOf(long postId) {
        return tx.execute(
                s ->
                        (Long)
                                org.springframework.test.util.ReflectionTestUtils.getField(
                                        crewPostRepository.findById(postId).orElseThrow(),
                                        "version"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "[임시:TC-성-11030·11031] 모집·수정·참석·참석 취소·삭제마다 SCHEDULE 신호가 크루원 전원(작성자 포함)에게 간다. 모집 푸시 설정과"
                    + " 상관없다")
    void scheduleSignals_forEveryListChange() {
        long leader = member();
        long a = member();
        long crewId = crew(leader, a);
        long run = crewRun(leader, crewId, LocalDateTime.now().plusDays(2).withNano(0));
        assertThat(recipientsOf(run, Resource.SCHEDULE)).containsExactlyInAnyOrder(leader, a);
        posts.answerAttendance(a, run, AttendanceAnswer.GOING);
        schedules.withdrawAttendance(a, run);
        schedules.edit(leader, run, new CrewRunEdit("새 제목", "본문", StandardDistance.KM5, null));
        posts.delete(leader, run);
        List<CrewRunChanged> schedule = changes(run, Resource.SCHEDULE);
        assertThat(schedule).hasSize(5);
        assertThat(schedule)
                .extracting(CrewRunChanged::revision)
                .isSorted()
                .doesNotHaveDuplicates();
        assertThat(changes(run, Resource.LOBBY)).as("대기실이 열리기 전에는 목록만").isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("[임시:TC-성-11040] 신호를 만든 트랜잭션이 롤백되면 신호 0건이고 순번도 그대로다")
    void rollback_sendsNothing() {
        long leader = member();
        long crewId = crew(leader);
        long run = crewRun(leader, crewId, in20Minutes());
        long before = crewRuns.readLobby(leader, run).revision();
        sent.clear();
        tx.executeWithoutResult(
                status -> {
                    crewRuns.scheduleChanged(crewPostRepository.findCrewRunById(run).orElseThrow());
                    status.setRollbackOnly();
                });
        assertThat(sent).isEmpty();
        assertThat(crewRuns.readLobby(leader, run).revision()).isEqualTo(before);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "[임시:TC-성-11027·11043] 지금 시작 → startAt에 출발: CrewRunDeparture는 입장자 전원(미준비 포함), 기존"
                    + " CrewRunStart는 확정 후보에게만, 둘 다 startAt = 예약 + 3초 그대로")
    void departure_recipients() {
        long leader = member();
        long a = member();
        long b = member();
        long c = member();
        long crewId = crew(leader, a, b, c);
        long run = crewRun(leader, crewId, in20Minutes());
        for (long m : List.of(leader, a, b)) {
            crewRuns.enterLobby(m, run);
        }
        crewRuns.setReady(leader, run, true);
        crewRuns.setReady(a, run, true);
        LobbyView planned = crewRuns.startNow(leader, run);
        assertThat(planned.phase()).isEqualTo(CrewRunPhase.COUNTDOWN);
        LocalDateTime startAt = planned.startAt();

        crewRuns.tick(startAt.minusSeconds(1));
        assertThat(crewRuns.readLobby(a, run).phase()).isEqualTo(CrewRunPhase.COUNTDOWN);
        crewRuns.tick(startAt);

        CrewRunDeparture departure =
                sent.stream()
                        .filter(s -> s.message() instanceof CrewRunDeparture)
                        .map(s -> (CrewRunDeparture) s.message())
                        .findFirst()
                        .orElseThrow();
        List<Long> departureTo =
                sent.stream()
                        .filter(s -> s.message() instanceof CrewRunDeparture)
                        .flatMap(s -> s.to().stream())
                        .toList();
        assertThat(departureTo).containsExactlyInAnyOrder(leader, a, b);
        assertThat(departure.participantIds()).containsExactly(leader, a);
        assertThat(departure.startAt()).isEqualTo(startAt);
        assertThat(departure.countdownToken()).isEqualTo(planned.countdownToken());
        assertThat(departure.crewId()).isEqualTo(crewId);
        List<Long> startTo =
                sent.stream()
                        .filter(s -> s.message() instanceof CrewRunStart)
                        .flatMap(s -> s.to().stream())
                        .toList();
        assertThat(startTo).containsExactlyInAnyOrder(leader, a);
        assertThat(
                        sent.stream()
                                .filter(s -> s.message() instanceof CrewRunStart)
                                .map(s -> ((CrewRunStart) s.message()).startAt()))
                .containsOnly(startAt);
        LobbyView bView = crewRuns.readLobby(b, run);
        assertThat(bView.meRunner()).isFalse();
        assertThat(bView.canJoin()).as("미준비 입장자는 함께 뛰기를 고른다").isTrue();
        assertThat(crewRuns.readLobby(c, run).canJoin()).as("미입장 크루원도 참여할 수 있다").isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "[임시:TC-성-11046] 중간 참여 커밋 뒤 Redis 반영이 실패해도 1초 시계가 기존 세션에 누락 참가자를 보충한다(개인 시작 시각 유지, 기존"
                    + " 참가자 거리 초기화 0)")
    void join_redisFailure_isSupplementedByTick() {
        long leader = member();
        long a = member();
        long c = member();
        long crewId = crew(leader, a, c);
        long run = crewRun(leader, crewId, in20Minutes());
        for (long m : List.of(leader, a)) {
            crewRuns.enterLobby(m, run);
            crewRuns.setReady(m, run, true);
        }
        LocalDateTime startAt = crewRuns.readLobby(a, run).startAt();
        crewRuns.tick(startAt);
        live.onProgress(
                a,
                run,
                new ProgressReport(Distance.ofMeters(300), Duration.ofSeconds(90), startAt),
                startAt.plusSeconds(90));

        doThrow(new IllegalStateException("Redis 쓰기 실패"))
                .doCallRealMethod()
                .when(live)
                .join(anyLong(), anyLong(), any(DisplayName.class), any(LocalDateTime.class));
        ParticipationView joined = crewRuns.join(c, run);
        assertThat(joined.alreadyJoined()).isFalse();
        assertThat(sessions.load(run).orElseThrow().participants()).doesNotContain(c);
        assertThat(changes(run, Resource.PROGRESS)).isNotEmpty();
        assertThat(changes(run, Resource.RESULT)).isNotEmpty();

        crewRuns.tick(startAt.plusSeconds(100));
        CrewRunLiveSession session = sessions.load(run).orElseThrow();
        assertThat(session.participants()).containsExactly(leader, a, c);
        assertThat(session.startOf(c)).isEqualTo(joined.joinedAt());
        assertThat(session.startOf(a)).isEqualTo(startAt);
        assertThat(session.latestOf(a).orElseThrow().distance()).isEqualTo(Distance.ofMeters(300));
        crewRuns.tick(startAt.plusSeconds(101));
        assertThat(sessions.load(run).orElseThrow().participants())
                .as("두 번 보충하지 않는다")
                .containsExactly(leader, a, c);
        ParticipationView again = crewRuns.join(c, run);
        assertThat(again.alreadyJoined()).isTrue();
        assertThat(again.joinedAt()).isEqualTo(joined.joinedAt());
        assertThat(again.measurementRecordId()).isEqualTo(joined.measurementRecordId());
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "결과 신호: 기록을 이으면 RESULT, 그 기록의 크루 기여가 더해져도 RESULT(나중 통지가 순번을 올린다, 05-32 4.5)")
    void resultSignals_onLinkAndContribution() {
        long leader = member();
        long a = member();
        long crewId = crew(leader, a);
        long run = crewRun(leader, crewId, in20Minutes());
        for (long m : List.of(leader, a)) {
            crewRuns.enterLobby(m, run);
            crewRuns.setReady(m, run, true);
        }
        crewRuns.tick(crewRuns.readLobby(a, run).startAt());
        sent.clear();
        String record = "rt-record-00000000000000000000000001";
        LocalDateTime start = LocalDateTime.now();
        crewRuns.onRunSaved(
                new RunRecordSaved(
                        record,
                        a,
                        start,
                        Distance.ofMeters(500),
                        Duration.ofMinutes(3),
                        false,
                        null,
                        RunMode.CREW_RUN,
                        null,
                        run,
                        true,
                        0,
                        start));
        assertThat(changes(run, Resource.RESULT)).hasSize(1);
        crewRuns.onContributed(
                new RaceContributionAdded(
                        crewId, a, record, Distance.ofMeters(500), start, LocalDateTime.now()));
        List<CrewRunChanged> result = changes(run, Resource.RESULT);
        assertThat(result).hasSize(2);
        assertThat(result.get(1).revision()).isGreaterThan(result.get(0).revision());
        crewRuns.onContributed(
                new RaceContributionAdded(
                        crewId, a, "unknown-record", Distance.ofMeters(1), start, start));
        assertThat(changes(run, Resource.RESULT)).as("크루런과 이어지지 않은 기록은 신호 없음").hasSize(2);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "[임시:TC-성-11026] 전원 준비·지금 시작·예약 시각 출발이 동시에 와도 출발 예약은 하나(같은 token·startAt), 세션도 하나")
    void concurrentStartPaths_singlePlan() throws Exception {
        long leader = member();
        long a = member();
        long crewId = crew(leader, a);
        LocalDateTime at = LocalDateTime.now().plusMinutes(1).withNano(0);
        long run = crewRun(leader, crewId, at);
        crewRuns.enterLobby(leader, run);
        crewRuns.enterLobby(a, run);
        crewRuns.setReady(leader, run, true);

        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch go = new CountDownLatch(1);
        List<Callable<String>> paths =
                List.of(
                        () -> {
                            go.await();
                            return crewRuns.setReady(a, run, true).countdownToken();
                        },
                        () -> {
                            go.await();
                            try {
                                return crewRuns.startNow(leader, run).countdownToken();
                            } catch (com.a207.runkk.common.BusinessRuleException e) {
                                return "not-ready"; // a가 아직 준비 전이면 최초 예약 부족
                            }
                        },
                        () -> {
                            go.await();
                            crewRuns.tick(at);
                            return "tick";
                        });
        List<Future<String>> futures = new ArrayList<>();
        for (Callable<String> path : paths) {
            futures.add(pool.submit(path));
        }
        go.countDown();
        for (Future<String> f : futures) {
            f.get();
        }
        pool.shutdown();

        LobbyView view = crewRuns.readLobby(leader, run);
        String token = view.countdownToken();
        assertThat(token).isNotNull();
        for (Future<String> f : futures) {
            String got = f.get();
            if (got != null && !got.equals("tick") && !got.equals("not-ready")) {
                assertThat(got).isEqualTo(token);
            }
        }
        assertThat(view.countdownParticipantIds()).containsExactlyInAnyOrder(leader, a);
        crewRuns.tick(view.startAt());
        crewRuns.tick(view.startAt().plusSeconds(1));
        LobbyView running = crewRuns.readLobby(leader, run);
        assertThat(running.phase()).isEqualTo(CrewRunPhase.RUNNING);
        assertThat(running.countdownToken()).isEqualTo(token);
        assertThat(running.startAt()).isEqualTo(view.startAt());
        assertThat(
                        sent.stream()
                                .filter(s -> s.message() instanceof CrewRunDeparture)
                                .map(s -> ((CrewRunDeparture) s.message()).countdownToken())
                                .distinct())
                .containsExactly(token);
        assertThat(sessions.load(run)).isPresent();
        doCallRealMethod().when(live).join(anyLong(), anyLong(), any(), any());
    }
}
