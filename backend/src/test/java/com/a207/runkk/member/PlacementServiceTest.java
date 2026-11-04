package com.a207.runkk.member;

import static com.a207.runkk.member.MemberFixtures.NOW;
import static com.a207.runkk.member.PlacementProgressTest.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.audit.AuditService;
import com.a207.runkk.audit.InMemoryAuditRepository;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.policy.InMemoryOperationSettingRepository;
import com.a207.runkk.policy.OperationPolicy;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * PlacementService(05-21 2.1): 진행 조회 세 상태(D-488), 판 셈 잠금·멱등·행 생성 금지(D-516), 잠근 뒤 랭크면 비어 있음(D-518),
 * 탈퇴 때 행 삭제(D-489). TierLevelService의 읽기 순서(D-517)도 여기서 본다.
 */
@DisplayName("배치고사 진행 서비스(PlacementService)")
class PlacementServiceTest {

    private final InMemoryMemberRepository members = new InMemoryMemberRepository();
    private final InMemoryPlacementProgressRepository progress =
            new InMemoryPlacementProgressRepository();
    private final OperationPolicy policy =
            new OperationPolicy(new InMemoryOperationSettingRepository());
    private final PlacementService service = new PlacementService(members, progress, policy);

    private long saved(String nickname) {
        return members.save(MemberFixtures.member(nickname)).getId();
    }

    @Test
    @DisplayName("TC-5757 언랭 회원: 진행 행이 없어도 IN_PROGRESS 0/3, 다음 판 1, 임시 티어 없음")
    void view_unrankedWithoutRow_inProgressZero() {
        long id = saved("새곰");

        PlacementView view = service.view(id);

        assertThat(view)
                .isEqualTo(
                        new PlacementView(
                                PlacementStatus.IN_PROGRESS, 0, 1, null, Tier.UNRANKED, List.of()));
        assertThat(progress.has(id)).as("조회는 행을 만들지 않는다").isFalse();
    }

    @Test
    @DisplayName("TC-5758 1판을 센 언랭: IN_PROGRESS 1/3, 다음 판 2, 임시 티어는 본인 조회에만")
    void view_afterOneRound_hasProvisional() {
        long id = saved("새곰");
        PlacementProgress p = PlacementProgress.start(id);
        progress.put(p);
        service.countRound(id, input(11, 27, 1));

        PlacementView view = service.view(id);

        assertThat(view.status()).isEqualTo(PlacementStatus.IN_PROGRESS);
        assertThat(view.countedRounds()).isEqualTo(1);
        assertThat(view.nextRound()).isEqualTo(2);
        assertThat(view.provisionalTier()).isEqualTo(Tier.BRONZE);
        assertThat(view.tier()).isEqualTo(Tier.UNRANKED);
        assertThat(members.summaries(List.of(id)).get(id).tier())
                .as("다른 사람이 읽는 티어는 그대로 언랭(D-485)")
                .isEqualTo(Tier.UNRANKED);
    }

    @Test
    @DisplayName("TC-5767·TC-5759 3판을 세면 회원 티어 확정 → COMPLETED, 판 3개, 다음 판·임시 티어 없음")
    void countRound_third_confirmsMemberTier() {
        long id = saved("새곰");
        progress.put(PlacementProgress.start(id));
        service.countRound(id, input(1, 41, 1));
        service.countRound(id, input(2, 41, 2));

        PlacementCount third = service.countRound(id, input(3, 41, 3));

        assertThat(third).isEqualTo(new PlacementCount.Confirmed(Tier.PLATINUM));
        assertThat(members.findById(id).orElseThrow().getTier()).isEqualTo(Tier.PLATINUM);
        PlacementView view = service.view(id);
        assertThat(view.status()).isEqualTo(PlacementStatus.COMPLETED);
        assertThat(view.countedRounds()).isEqualTo(3);
        assertThat(view.nextRound()).isNull();
        assertThat(view.provisionalTier()).isNull();
        assertThat(view.rounds()).hasSize(3);
        assertThat(service.isCompleted(id)).isTrue();
        assertThat(service.isCounted(id, 3)).isTrue();
    }

    @Test
    @DisplayName("TC-5760 배치고사 없이 티어가 있던 회원은 EXEMPT 0판(브리프 규칙 12)")
    void view_rankedWithoutPlacement_exempt() {
        long id = saved("옛곰");
        members.findById(id).orElseThrow().confirmPlacement(Tier.GOLD);

        assertThat(service.view(id))
                .isEqualTo(
                        new PlacementView(
                                PlacementStatus.EXEMPT, 0, null, null, Tier.GOLD, List.of()));
        assertThat(service.isCompleted(id)).isFalse();
    }

    @Test
    @DisplayName("TC-5763 행이 없으면 세지 않고 만들지도 않는다(탈퇴로 지워진 경우, D-516)")
    void countRound_withoutRow_ignoredAndNotCreated() {
        long id = saved("새곰");

        assertThat(service.countRound(id, input(1, 30, 1)))
                .isEqualTo(new PlacementCount.Ignored("no-progress"));
        assertThat(progress.has(id)).isFalse();
        assertThat(progress.calls()).contains("findByIdForUpdate").doesNotContain("lockOrCreate");
    }

    @Test
    @DisplayName("TC-5764·TC-5765 탈퇴 회원·이미 랭크 티어 회원은 Ignored(D-516)")
    void countRound_inactiveOrRanked_ignored() {
        long gone = saved("떠난곰");
        progress.put(PlacementProgress.start(gone));
        members.findById(gone).orElseThrow().withdraw(NOW);
        long ranked = saved("옛곰");
        progress.put(PlacementProgress.start(ranked));
        members.findById(ranked).orElseThrow().confirmPlacement(Tier.SILVER);

        assertThat(service.countRound(gone, input(1, 30, 1)))
                .isEqualTo(new PlacementCount.Ignored("inactive"));
        assertThat(service.countRound(ranked, input(2, 30, 1)))
                .isEqualTo(new PlacementCount.Ignored("ranked"));
    }

    @Test
    @DisplayName("TC-5766 같은 판을 두 계기가 불러도 한 번만 센다(D-516)")
    void countRound_sameBattleTwice_countedOnce() {
        long id = saved("새곰");
        progress.put(PlacementProgress.start(id));

        assertThat(service.countRound(id, input(7, 30, 1)))
                .isInstanceOf(PlacementCount.Provisional.class);
        assertThat(service.countRound(id, input(7, 30, 1)))
                .isEqualTo(new PlacementCount.Ignored("duplicate"));
        assertThat(service.view(id).countedRounds()).isEqualTo(1);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("PL1-B6 판 셈 운영 로그에는 결과 종류와 판 번호만 남고 임시 티어는 남지 않는다(D-485)")
    void countRound_logOmitsProvisionalTier(CapturedOutput output) {
        long id = saved("새곰");
        progress.put(PlacementProgress.start(id));

        service.countRound(id, input(11, 27, 1));

        String line =
                output.getAll()
                        .lines()
                        .filter(l -> l.contains("placement_round_counted"))
                        .findFirst()
                        .orElseThrow();
        assertThat(line).contains("result=Provisional", "round=1").doesNotContain("BRONZE");
    }

    @Test
    @DisplayName("TC-5761·TC-5762 lockForMatch: 언랭이면 행을 만들고 다음 판·임시 티어, 잠근 뒤 랭크면 비어 있다(D-518)")
    void lockForMatch_createsRow_orEmptyWhenRanked() {
        long id = saved("새곰");

        assertThat(service.lockForMatch(id)).contains(new PlacementBasis(1, null));
        assertThat(progress.has(id)).isTrue();

        members.findById(id).orElseThrow().confirmPlacement(Tier.GOLD);
        assertThat(service.lockForMatch(id)).isEmpty();
    }

    @Test
    @DisplayName("PL1-B3 lockForMatch: 잠근 뒤 회원이 탈퇴(ACTIVE 아님)면 MATCH_FAILED로 되돌린다(J24-9)")
    void lockForMatch_withdrawnMember_fails() {
        long id = saved("떠난곰");
        members.findById(id).orElseThrow().withdraw(NOW);

        assertThatThrownBy(() -> service.lockForMatch(id))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.MATCH_FAILED);
    }

    @Test
    @DisplayName("TC-5769 정정은 센 판의 승패만 바꾸고 행이 없으면 아무것도 하지 않는다")
    void correctVerdict_onlyCountedRound() {
        long id = saved("새곰");
        progress.put(PlacementProgress.start(id));
        service.countRound(id, input(5, 30, 1));

        service.correctVerdict(id, 5, PlacementVerdict.DRAW);
        service.correctVerdict(999L, 5, PlacementVerdict.LOSE);

        assertThat(service.view(id).rounds().get(0).verdict()).isEqualTo(PlacementVerdict.DRAW);
        assertThat(progress.has(999L)).isFalse();
    }

    @Test
    @DisplayName("TC-5772 탈퇴 두 경로(본인 탈퇴, 미이용 게스트 파기 공통 연산)는 가드보다 먼저 진행 행을 잠그고 지운다(D-489, PL1-B3)")
    void withdraw_deletesPlacementRow() {
        long id = saved("떠날곰");
        progress.put(PlacementProgress.start(id));
        List<String> lockedWhenGuarded = new java.util.ArrayList<>();
        WithdrawalGuard guard =
                memberId -> {
                    lockedWhenGuarded.addAll(progress.calls());
                    return Optional.empty();
                };
        WithdrawalService withdrawal =
                new WithdrawalService(
                        members,
                        progress,
                        List.of(guard),
                        List.of(),
                        new AuditService(
                                new InMemoryAuditRepository(),
                                Clock.fixed(
                                        NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                                        ZoneId.of("Asia/Seoul"))),
                        e -> {},
                        Clock.fixed(
                                NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                                ZoneId.of("Asia/Seoul")));

        withdrawal.withdraw(id);

        assertThat(progress.has(id)).isFalse();
        assertThat(progress.calls())
                .as("판 셈과 같은 잠금 순서: 진행 행을 먼저 잠근다(교착 방지)")
                .containsSubsequence("lockOrCreate", "deleteByMemberId");
        assertThat(lockedWhenGuarded)
                .as("PL1-B3 가드보다 먼저 진행 행을 잠근다(INSERT … ON CONFLICT 뒤 잠금으로 커밋 전 매칭과 직렬화)")
                .containsExactly("lockOrCreate");
        assertThat(service.countRound(id, input(1, 30, 1)))
                .as("탈퇴 뒤 셈·복구가 행을 되살리지 않는다")
                .isEqualTo(new PlacementCount.Ignored("no-progress"));
        assertThat(progress.has(id)).isFalse();
    }

    @Test
    @DisplayName(
            "TC-5770 TierLevelService: Member를 먼저 읽고 진행 행을 그다음 읽는다. 셈이 두 읽기 사이에 커밋돼도(진행 = 확정 10,"
                    + " Member = 언랭) 재평가가 없다(D-517)")
    void tierLevelService_readsMemberFirst_noReevaluationOnRace() {
        MemberRepository memberRepo = mock(MemberRepository.class);
        PlacementProgressRepository progressRepo = mock(PlacementProgressRepository.class);
        Member unranked = MemberFixtures.member("경합곰");
        for (int i = 1; i <= 9; i++) {
            unranked.recordValidRun(
                    new RunScoreInput(46, Distance.ofKm(5), i), policy.tierBoundary(), 0);
        }
        when(memberRepo.findById(anyLong())).thenReturn(Optional.of(unranked));
        PlacementProgress committed = PlacementProgress.start(1L);
        committed.count(input(1, 46, StandardDistance.KM5, 8), policy.tierBoundary());
        committed.count(input(2, 46, StandardDistance.KM5, 9), policy.tierBoundary());
        committed.count(input(3, 46, StandardDistance.KM5, 10), policy.tierBoundary());
        when(progressRepo.findById(anyLong())).thenReturn(Optional.of(committed));
        TierLevelService tierLevel = new TierLevelService(memberRepo, progressRepo, policy);

        TierChange change =
                tierLevel.recordValidRun(1L, new RunScoreInput(46, Distance.ofKm(5), 10));

        assertThat(change.evaluation()).isEmpty();
        assertThat(unranked.getTier()).isEqualTo(Tier.UNRANKED);
        InOrder order = inOrder(memberRepo, progressRepo);
        order.verify(memberRepo).findById(1L);
        order.verify(progressRepo).findById(1L);
    }

    @Test
    @DisplayName("TC-5771 TierLevelService: 진행 행이 없는 기존 티어 회원은 확정 순번 0으로 넘겨 10회째 재평가한다")
    void tierLevelService_noProgressRow_passesZeroOrdinal() {
        long id = saved("옛곰");
        members.findById(id).orElseThrow().confirmPlacement(Tier.BRONZE);
        TierLevelService tierLevel = new TierLevelService(members, progress, policy);
        for (int i = 1; i <= 9; i++) {
            tierLevel.recordValidRun(id, new RunScoreInput(46, Distance.ofKm(5), i));
        }

        TierChange tenth =
                tierLevel.recordValidRun(id, new RunScoreInput(46, Distance.ofKm(5), 10));

        assertThat(tenth.evaluation()).isPresent();
        assertThat(members.findById(id).orElseThrow().getTier()).isEqualTo(Tier.MASTER);
    }

    @Test
    @DisplayName("TierLevelService: 확정 판 순번(10)이면 같은 10번째 러닝은 재평가하지 않는다(D-484)")
    void tierLevelService_passesConfirmedOrdinal() {
        long id = saved("확정곰");
        TierLevelService tierLevel = new TierLevelService(members, progress, policy);
        for (int i = 1; i <= 9; i++) {
            tierLevel.recordValidRun(id, new RunScoreInput(46, Distance.ofKm(5), i));
        }
        progress.put(PlacementProgress.start(id));
        service.countRound(id, input(1, 30, 8));
        service.countRound(id, input(2, 30, 9));
        service.countRound(id, input(3, 30, 10));
        assertThat(members.findById(id).orElseThrow().getTier()).isEqualTo(Tier.SILVER);

        TierChange tenth =
                tierLevel.recordValidRun(id, new RunScoreInput(46, Distance.ofKm(5), 10));

        assertThat(tenth.evaluation()).isEmpty();
        assertThat(members.findById(id).orElseThrow().getTier()).isEqualTo(Tier.SILVER);
    }
}
