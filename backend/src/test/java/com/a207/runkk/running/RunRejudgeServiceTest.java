package com.a207.runkk.running;

import static com.a207.runkk.running.OverspeedFixtures.pathMeters;
import static com.a207.runkk.running.OverspeedFixtures.shortJump;
import static com.a207.runkk.running.OverspeedFixtures.teleportOnly;
import static com.a207.runkk.running.RunFixtures.START;
import static com.a207.runkk.running.RunFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.a207.runkk.NoTransactions;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.member.InMemoryMemberRepository;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.RunScoreInput;
import com.a207.runkk.member.TierLevelService;
import com.a207.runkk.policy.InMemoryOperationSettingRepository;
import com.a207.runkk.policy.OperationPolicy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * v0.10 일회성 재판정(사용자 결정, RunRejudgeService). V28이 PENDING을 세운 무효 기록만 새 규칙으로 다시 판정하고, 유효가 된 기록은 최고 기록
 * 장부·티어(늦은 반영)·RunRecordRejudged(리워드·배경, 알림 없음)를 한 번만 맞춘다. 경로가 없는 기록은 SKIPPED로 남긴다.
 */
@DisplayName("v0.10 일회성 재판정(RunRejudgeService)")
class RunRejudgeServiceTest {

    private static final LocalDateTime SAVED = LocalDateTime.of(2026, 10, 26, 9, 0);

    /** 옛 규칙(최고 시속 25km/h 초과면 무효)으로 판정된 상태를 흉내 낸다: 선언 값 그대로 무효, 제외량 0. */
    private static final ValidityRule OLD_INVALID =
            new ValidityRule(
                    Distance.ofKm(100_000),
                    Duration.ofMinutes(3),
                    new Pace(180),
                    new Pace(900),
                    Double.MAX_VALUE);

    private final Set<String> broken = new java.util.HashSet<>();
    private final InMemoryRunningRepositories.Records records =
            new InMemoryRunningRepositories.Records() {
                @Override
                public Optional<RunRecord> findForUpdate(String recordId) {
                    if (broken.contains(recordId)) {
                        throw new IllegalStateException("장애");
                    }
                    return super.findForUpdate(recordId);
                }
            };
    private final InMemoryRunningRepositories.Books books = new InMemoryRunningRepositories.Books();
    private final InMemoryMemberRepository members = new InMemoryMemberRepository();
    private final MemberQueryService memberQuery = spy(new MemberQueryService(members));
    private final TierLevelService tiers =
            spy(
                    new TierLevelService(
                            members,
                            new OperationPolicy(new InMemoryOperationSettingRepository())));
    private final List<DomainEvent> events = new ArrayList<>();
    private final RunRejudgeService service =
            new RunRejudgeService(
                    records,
                    books,
                    new TierApplier(tiers, memberQuery, records),
                    memberQuery,
                    events::add,
                    NoTransactions.manager());
    private int seq;

    private long member() {
        return members.save(MemberFixtures.member("재판정" + (members.size() + 10))).getId();
    }

    /** 배포 전에 저장된 무효 기록(V28이 state 표시를 세운 상태). */
    private RunRecord stored(
            long memberId, List<LocationPoint> points, long declared, int seconds, RejudgeState s) {
        RunRecord r =
                RunRecord.receive(
                        memberId,
                        OverspeedFixtures.upload(
                                id(++seq), points, declared, Duration.ofSeconds(seconds)));
        r.judgeValidity(OLD_INVALID);
        r.markSaved(SAVED.plusMinutes(seq));
        r.markRejudgeState(s);
        records.save(r);
        return r;
    }

    private RunRecord jumpRun(long memberId, RejudgeState s) {
        List<LocationPoint> points = shortJump(START.plusHours(seq));
        return stored(memberId, points, pathMeters(points), 928, s);
    }

    @Test
    @DisplayName("PENDING 표시가 있는 무효 기록만 고른다: 표시 없는 무효 기록·DONE은 그대로")
    void picksOnlyPending() {
        long m = member();
        RunRecord pending = jumpRun(m, RejudgeState.PENDING);
        RunRecord newUpload = jumpRun(m, null);
        RunRecord done = jumpRun(m, RejudgeState.DONE);

        RunRejudgeService.Result result = service.run();

        assertThat(result).isEqualTo(new RunRejudgeService.Result(1, 0, 0, 1, 0));
        assertThat(pending.isValid()).isTrue();
        assertThat(pending.getDistance()).isEqualTo(Distance.ofMeters(3_090));
        assertThat(pending.getElapsed()).isEqualTo(Duration.ofSeconds(927));
        assertThat(pending.getExcludedDistance()).isEqualTo(Distance.ofMeters(50));
        assertThat(pending.rejudgeState()).isEqualTo(RejudgeState.DONE);
        assertThat(newUpload.isValid()).isFalse();
        assertThat(newUpload.rejudgeState()).isNull();
        assertThat(done.isValid()).isFalse();
        assertThat(done.getDistance()).isEqualTo(Distance.ofMeters(3_140));
    }

    @Test
    @DisplayName("한 번 실행은 상한 수까지만 처리하고 남은 기록은 다음 실행이 잇는다(D-199)")
    void stopsAtRecordBudget() {
        long m = member();
        jumpRun(m, RejudgeState.PENDING);
        jumpRun(m, RejudgeState.PENDING);
        jumpRun(m, RejudgeState.PENDING);
        RunRejudgeService.Budget two = new RunRejudgeService.Budget(2, Duration.ofMinutes(1));

        assertThat(service.run(two)).isEqualTo(new RunRejudgeService.Result(2, 0, 0, 0, 0));
        assertThat(service.run(two)).isEqualTo(new RunRejudgeService.Result(1, 0, 0, 1, 0));
        assertThat(service.run(two)).isEqualTo(new RunRejudgeService.Result(0, 0, 0, 2, 0));
        assertThat(service.run(two)).isEqualTo(new RunRejudgeService.Result(0, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("시간 상한이 지나면 더 고르지 않고 표시를 그대로 둔다(D-199)")
    void stopsAtTimeBudget() {
        RunRecord r = jumpRun(member(), RejudgeState.PENDING);

        RunRejudgeService.Result result =
                service.run(new RunRejudgeService.Budget(1_000, Duration.ZERO));

        assertThat(result).isEqualTo(new RunRejudgeService.Result(0, 0, 0, 0, 0));
        assertThat(r.rejudgeState()).isEqualTo(RejudgeState.PENDING);
    }

    @Test
    @DisplayName("유효가 되면 최고 기록 장부·티어(반영 표시)·RunRecordRejudged(집계 거리, 원래 저장 일시)를 맞춘다")
    void sideEffectsForNowValid() {
        long m = member();
        RunRecord r = jumpRun(m, RejudgeState.PENDING);

        service.run();

        assertThat(r.isTierApplied()).isTrue();
        ArgumentCaptor<RunScoreInput> input = ArgumentCaptor.forClass(RunScoreInput.class);
        verify(tiers).recordValidRun(org.mockito.ArgumentMatchers.eq(m), input.capture());
        assertThat(input.getValue().distance()).isEqualTo(Distance.ofMeters(3_090));
        assertThat(input.getValue().validRunOrdinal()).isEqualTo(1);
        assertThat(input.getValue().score()).isEqualTo(r.getScore().value());
        assertThat(books.findOrCreate(m).best(StandardDistance.KM1)).isPresent();
        assertThat(r.getNewRecords()).isEmpty();
        assertThat(events)
                .containsExactly(
                        new RunRecordRejudged(
                                r.getRecordId(), m, Distance.ofMeters(3_090), r.getSavedAt()));
    }

    @Test
    @DisplayName("멱등: 다시 돌리면 고를 것이 없고 티어·사건을 다시 내지 않는다")
    void idempotent() {
        long m = member();
        RunRecord r = jumpRun(m, RejudgeState.PENDING);
        service.run();

        assertThat(service.run()).isEqualTo(new RunRejudgeService.Result(0, 0, 0, 0, 0));
        verify(tiers, times(1)).recordValidRun(anyLong(), any());
        assertThat(events).hasSize(1);
        assertThat(r.getDistance()).isEqualTo(Distance.ofMeters(3_090));
    }

    @Test
    @DisplayName("순간이동만 있는 기록은 무효 그대로 DONE이고 남은 거리(360m)·제외 거리를 남긴다, 부수 처리 없음")
    void stillInvalid() {
        long m = member();
        List<LocationPoint> points = teleportOnly(START);
        RunRecord r = stored(m, points, pathMeters(points), 121, RejudgeState.PENDING);

        assertThat(service.run()).isEqualTo(new RunRejudgeService.Result(0, 1, 0, 0, 0));
        assertThat(r.isValid()).isFalse();
        assertThat(r.getDistance()).isEqualTo(Distance.ofMeters(360));
        assertThat(r.getExcludedDistance().meters()).isGreaterThan(9_000_000L);
        assertThat(r.rejudgeState()).isEqualTo(RejudgeState.DONE);
        verify(tiers, never()).recordValidRun(anyLong(), any());
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("경로가 파기된 기록은 다시 판정할 수 없어 SKIPPED로 두고 수를 센다")
    void skipsPurgedRoute() {
        long m = member();
        RunRecord r = jumpRun(m, RejudgeState.PENDING);
        r.purgeRoute();

        assertThat(service.run()).isEqualTo(new RunRejudgeService.Result(0, 0, 1, 0, 0));
        assertThat(r.isValid()).isFalse();
        assertThat(r.getDistance()).isEqualTo(Distance.ofMeters(3_140));
        assertThat(r.rejudgeState()).isEqualTo(RejudgeState.SKIPPED);
    }

    @Test
    @DisplayName("비활성(탈퇴) 회원 기록은 유효로 바꾸되 티어·리워드·배경은 건드리지 않는다")
    void inactiveMemberHasNoSideEffects() {
        long m = member();
        RunRecord r = jumpRun(m, RejudgeState.PENDING);
        doReturn(false).when(memberQuery).isActive(m);

        service.run();

        assertThat(r.isValid()).isTrue();
        assertThat(r.rejudgeState()).isEqualTo(RejudgeState.DONE);
        assertThat(r.isTierApplied()).isFalse();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("한 기록이 실패해도 나머지를 마치고, 실패한 기록은 표시를 남겨 다음 실행이 잇는다")
    void failureKeepsStateForNextRun() {
        long m = member();
        RunRecord bad = jumpRun(m, RejudgeState.PENDING);
        RunRecord good = jumpRun(m, RejudgeState.PENDING);
        broken.add(bad.getRecordId());

        assertThat(service.run()).isEqualTo(new RunRejudgeService.Result(1, 0, 0, 1, 1));
        assertThat(good.isValid()).isTrue();
        assertThat(bad.rejudgeState()).isEqualTo(RejudgeState.PENDING);

        broken.clear();
        assertThat(service.run()).isEqualTo(new RunRejudgeService.Result(1, 0, 0, 1, 0));
        assertThat(bad.isValid()).isTrue();
    }
}
