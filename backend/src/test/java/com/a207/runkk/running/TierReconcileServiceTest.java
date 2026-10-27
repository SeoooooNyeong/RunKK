package com.a207.runkk.running;

import static com.a207.runkk.running.RunFixtures.START;
import static com.a207.runkk.running.RunFixtures.id;
import static com.a207.runkk.running.RunFixtures.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.a207.runkk.NoTransactions;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.InMemoryMemberRepository;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.RunScoreInput;
import com.a207.runkk.member.TierLevelService;
import com.a207.runkk.policy.InMemoryOperationSettingRepository;
import com.a207.runkk.policy.OperationPolicy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 시험 2.1.10.16(TC-6165~TC-6168)과 T-32 티어 부분(TC-6789·TC-6790). 총괄 결정(설계 쟁점 7-3): 보정 판단 근거는 순번 대신 기록별
 * 반영 표시 tier_applied다. TierReconcileService.reconcile(now)는 (오늘 − 2일) 0시부터 저장된 tier_applied =
 * false인 유효 기록을 저장 순서대로 반영하고 표시를 세운다. 이미 지난 순번(뒤 기록이 먼저 반영된 구멍)은 판정 없이 점수·거리만
 * 더한다(recordLateValidRun). 보정한 회원 수를 돌려준다.
 */
@DisplayName("티어 갱신 보정(D-108, L16, 총괄 결정 7-3)")
class TierReconcileServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 19, 0);
    private static final LocalDateTime IN_RANGE = LocalDateTime.of(2026, 10, 21, 8, 0);

    private final InMemoryRunningRepositories.Records records =
            new InMemoryRunningRepositories.Records();
    private final InMemoryMemberRepository members = new InMemoryMemberRepository();
    private final MemberQueryService memberQuery = spy(new MemberQueryService(members));
    private final TierLevelService tiers =
            spy(
                    new TierLevelService(
                            members,
                            new OperationPolicy(new InMemoryOperationSettingRepository())));
    private final TierApplier applier = new TierApplier(tiers, memberQuery, records);
    private final TierReconcileService service =
            new TierReconcileService(records, applier, NoTransactions.manager());
    private final Map<Long, List<String>> recordIds = new HashMap<>();
    private int seq;

    private long member() {
        return members.save(MemberFixtures.member("보정" + (members.size() + 10))).getId();
    }

    /** 유효 5km 25:00 기록 n건을 1분 간격으로, 마지막 것을 savedAt에 저장한다(반영 표시 없음). */
    private void runs(long memberId, int n, LocalDateTime savedAt) {
        for (int i = 0; i < n; i++) {
            seq++;
            RunRecord r =
                    RunRecord.receive(
                            memberId, upload(id(seq), START.plusHours(seq), 5_000, 1_500));
            r.judgeValidity(ValidityRule.STANDARD);
            r.markSaved(savedAt.minusMinutes(n - 1L - i));
            records.save(r);
            recordIds.computeIfAbsent(memberId, k -> new ArrayList<>()).add(r.getRecordId());
        }
    }

    /** 리스너가 순번 ordinals를 반영하고 그 기록에 표시를 세운 상태로 만든다. */
    private void listenerApplied(long memberId, int... ordinals) {
        for (int ordinal : ordinals) {
            String recordId = recordIds.get(memberId).get(ordinal - 1);
            applier.apply(memberId, recordId, new RunScoreInput(38.3, Distance.ofKm(5), ordinal));
        }
        clearInvocations(tiers, memberQuery);
    }

    private List<Integer> ordinalsGivenTo(long memberId, int count) {
        ArgumentCaptor<RunScoreInput> inputs = ArgumentCaptor.forClass(RunScoreInput.class);
        verify(tiers, times(count)).recordValidRun(eq(memberId), inputs.capture());
        return inputs.getAllValues().stream().map(RunScoreInput::validRunOrdinal).toList();
    }

    @Test
    @DisplayName("TC-6165 K1: (오늘 − 2일) 0시 전 저장 기록뿐이면 0, 티어 호출 0회")
    void tc6165_nothingInRange() {
        long m = member();
        runs(m, 3, LocalDateTime.of(2026, 10, 19, 23, 59, 59));
        assertThat(service.reconcile(NOW)).isZero();
        verify(memberQuery, never()).appliedValidRunCount(anyLong());
        verify(tiers, never()).recordValidRun(anyLong(), any());
    }

    @Test
    @DisplayName("TC-6166 K2: 유효 5건이 모두 반영 표시면 0, recordValidRun 0회")
    void tc6166_upToDate() {
        long m = member();
        runs(m, 5, IN_RANGE);
        listenerApplied(m, 1, 2, 3, 4, 5);
        assertThat(service.reconcile(NOW)).isZero();
        verify(tiers, never()).recordValidRun(anyLong(), any());
        verify(tiers, never()).recordLateValidRun(anyLong(), any());
    }

    @Test
    @DisplayName("TC-6167 K4: 유효 10·반영 8이면 순번 9, 10 순서로 넘기고 회원 1명 보정. 다시 돌리면 0(멱등, 표시)")
    void tc6167_fillsMissingOrdinalsInOrder() {
        long m = member();
        runs(m, 10, IN_RANGE);
        listenerApplied(m, 1, 2, 3, 4, 5, 6, 7, 8);

        assertThat(service.reconcile(NOW)).isEqualTo(1);
        assertThat(ordinalsGivenTo(m, 2)).containsExactly(9, 10);
        assertThat(memberQuery.appliedValidRunCount(m)).isEqualTo(10);
        assertThat(records.findById(recordIds.get(m).get(9)).orElseThrow().isTierApplied())
                .isTrue();
        assertThat(members.findById(m).orElseThrow().getTier()).isNotEqualTo(Tier.UNRANKED);

        clearInvocations(tiers);
        assertThat(service.reconcile(NOW)).isZero();
        verify(tiers, never()).recordValidRun(anyLong(), any());
    }

    @Test
    @DisplayName("TC-6168 K5: 한 회원의 recordValidRun 예외는 로그 뒤 건너뛰고(표시 그대로) 다음 회원은 보정한다")
    void tc6168_failureDoesNotStopOthers() {
        long a = member();
        long b = member();
        runs(a, 1, IN_RANGE);
        runs(b, 1, IN_RANGE.plusHours(1));
        doThrow(new IllegalStateException("장애")).when(tiers).recordValidRun(eq(a), any());

        assertThat(service.reconcile(NOW)).isEqualTo(1);
        assertThat(memberQuery.appliedValidRunCount(a)).isZero();
        assertThat(records.findById(recordIds.get(a).get(0)).orElseThrow().isTierApplied())
                .isFalse();
        assertThat(memberQuery.appliedValidRunCount(b)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-6789: 하한 (오늘 − 2일) 0시 포함 — 10/20 00:00:00 저장은 대상, 10/19 23:59:59는 범위 밖")
    void tc6789_lowerBound() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 0, 10);
        long inside = member();
        long outside = member();
        runs(inside, 1, LocalDateTime.of(2026, 10, 20, 0, 0));
        runs(outside, 1, LocalDateTime.of(2026, 10, 19, 23, 59, 59));

        assertThat(service.reconcile(now)).isEqualTo(1);
        assertThat(memberQuery.appliedValidRunCount(inside)).isEqualTo(1);
        assertThat(memberQuery.appliedValidRunCount(outside)).isZero();
    }

    @Test
    @DisplayName("TC-6790(총괄 결정 7-3): 순번 3만 실패하고 4가 먼저 반영(D-40 배치)된 회원은 3을 판정 없이 늦게 더해 구멍을 메운다")
    void tc6790_holeIsFilledLate() {
        long m = member();
        runs(m, 4, IN_RANGE);
        listenerApplied(m, 1, 2, 4);
        assertThat(members.findById(m).orElseThrow().getTier()).isEqualTo(Tier.GOLD);
        assertThat(memberQuery.appliedValidRunCount(m)).isEqualTo(4);

        assertThat(service.reconcile(NOW)).isEqualTo(1);
        ArgumentCaptor<RunScoreInput> late = ArgumentCaptor.forClass(RunScoreInput.class);
        verify(tiers).recordLateValidRun(eq(m), late.capture());
        assertThat(late.getValue().validRunOrdinal()).isEqualTo(3);
        verify(tiers, never()).recordValidRun(anyLong(), any());
        assertThat(records.findById(recordIds.get(m).get(2)).orElseThrow().isTierApplied())
                .isTrue();
        assertThat(memberQuery.appliedValidRunCount(m)).isEqualTo(4);
        assertThat(members.findById(m).orElseThrow().getTier()).isEqualTo(Tier.GOLD);

        clearInvocations(tiers);
        assertThat(service.reconcile(NOW)).isZero();
        verify(tiers, never()).recordLateValidRun(anyLong(), any());
    }

    @Test
    @DisplayName("TC-6790 변형: 순번 3·4가 둘 다 빠지면 3을 먼저 넘겨 배치 판정(언랭 → 배치), 4는 그 뒤")
    void tc6790_placementAtThird() {
        long m = member();
        runs(m, 4, IN_RANGE);
        listenerApplied(m, 1, 2);
        assertThat(members.findById(m).orElseThrow().getTier()).isEqualTo(Tier.UNRANKED);

        assertThat(service.reconcile(NOW)).isEqualTo(1);
        assertThat(ordinalsGivenTo(m, 2)).containsExactly(3, 4);
        assertThat(members.findById(m).orElseThrow().getTier()).isEqualTo(Tier.GOLD);
    }

    @Test
    @DisplayName("무효 기록만 저장된 회원은 보정 대상이 아니다")
    void invalidOnlyIsIgnored() {
        long m = member();
        seq++;
        RunRecord r = RunRecord.receive(m, upload(id(seq), START, 900, 300));
        r.judgeValidity(ValidityRule.STANDARD);
        r.markSaved(IN_RANGE);
        records.save(r);
        assertThat(service.reconcile(NOW)).isZero();
        verify(memberQuery, never()).appliedValidRunCount(anyLong());
    }

    @Test
    @DisplayName("이미 표시가 선 기록을 다시 반영하려 하면 실패로 끝나 이중 반영이 없다")
    void applyTwiceIsRejected() {
        long m = member();
        runs(m, 1, IN_RANGE);
        listenerApplied(m, 1);
        String recordId = recordIds.get(m).get(0);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                applier.apply(
                                        m,
                                        recordId,
                                        new RunScoreInput(
                                                38.3,
                                                records.findById(recordId)
                                                        .orElseThrow()
                                                        .getDistance(),
                                                1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
