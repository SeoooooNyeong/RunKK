package com.a207.runkk.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.battle.BattleResultService;
import com.a207.runkk.battle.BattleWin;
import com.a207.runkk.common.Distance;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.RewardRule;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunRecordSaved;
import com.a207.runkk.running.SavedRun;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 리워드 일 단위 보정(코드 리뷰 2차 M4 결정): 빠진 적립 채우기, 멱등, 원래 사건일 하루 상한. 인메모리 지갑·고정 시각. */
@DisplayName("리워드 일 단위 보정")
class RewardReconcileServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 0, 10);
    private static final LocalDateTime YESTERDAY = NOW.minusDays(1).withHour(18);
    private static final long ALICE = 1;
    private static final long WITHDRAWN = 3;

    private final InMemoryRewardWalletRepository wallets = new InMemoryRewardWalletRepository();
    private final InMemoryRewardAccrualRepository accruals = new InMemoryRewardAccrualRepository();
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final RunQueryService runs = mock(RunQueryService.class);
    private final BattleResultService battles = mock(BattleResultService.class);
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final RewardService rewards =
            new RewardService(wallets, accruals, policy, new DirectTx());
    private final RewardReconcileService reconcile =
            new RewardReconcileService(rewards, runs, battles, members, policy);
    private final List<SavedRun> saved = new ArrayList<>();
    private final List<BattleWin> wins = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(policy.rewardRule()).thenReturn(new RewardRule(10, 20, 200));
        when(members.isActive(anyLong())).thenReturn(true);
        when(members.isActive(WITHDRAWN)).thenReturn(false);
        LocalDateTime from = NOW.toLocalDate().minusDays(2).atStartOfDay();
        when(runs.savedBetween(from, NOW)).thenReturn(saved);
        when(battles.winsJudgedBetween(from, NOW)).thenReturn(wins);
    }

    private static SavedRun run(String id, long member, long meters, LocalDateTime savedAt) {
        return new SavedRun(
                id, member, savedAt.minusHours(1), Distance.ofMeters(meters), true, null, savedAt);
    }

    private static RunRecordSaved event(String id, long meters, LocalDateTime savedAt) {
        return new RunRecordSaved(
                id,
                ALICE,
                savedAt.minusHours(1),
                Distance.ofMeters(meters),
                Duration.ofMinutes(30),
                true,
                40.0,
                RunMode.FREE,
                null,
                null,
                true,
                1,
                savedAt);
    }

    @Test
    @DisplayName("수신자가 실패한 유효 기록 1건을 채우고, 다시 돌리면 0건이다")
    void restoresMissedRunOnce() {
        wallets.failNext(new IllegalStateException("db down"));
        rewards.onRunSaved(event("r-missed", 5_000, YESTERDAY));
        rewards.onRunSaved(event("r-ok", 3_000, YESTERDAY));
        saved.add(run("r-missed", ALICE, 5_000, YESTERDAY));
        saved.add(run("r-ok", ALICE, 3_000, YESTERDAY));
        assertThat(rewards.balanceOf(ALICE)).isEqualTo(30);

        assertThat(reconcile.reconcile(NOW)).isEqualTo(1);
        assertThat(rewards.accruedFor(AccrualReason.VALID_RUN, "r-missed", ALICE)).isEqualTo(50);
        assertThat(reconcile.reconcile(NOW)).isZero();
        assertThat(rewards.balanceOf(ALICE)).isEqualTo(80);
    }

    @Test
    @DisplayName("하루 상한은 보정하는 날이 아니라 원래 사건일 기준: 전날 남은 10만 채우고 오늘 상한은 그대로다")
    void dailyCapCountsOriginalDay() {
        rewards.onRunSaved(event("r-big", 19_000, YESTERDAY)); // 전날 190
        saved.add(run("r-big", ALICE, 19_000, YESTERDAY));
        saved.add(run("r-late", ALICE, 5_000, YESTERDAY.plusHours(1))); // 수신자 유실분

        assertThat(reconcile.reconcile(NOW)).isEqualTo(1);
        assertThat(rewards.accruedFor(AccrualReason.VALID_RUN, "r-late", ALICE)).isEqualTo(10);

        rewards.onRunSaved(event("r-today", 20_000, NOW)); // 오늘 상한 200은 전날과 따로
        assertThat(rewards.accruedFor(AccrualReason.VALID_RUN, "r-today", ALICE)).isEqualTo(200);
    }

    @Test
    @DisplayName("전날 상한이 이미 찼으면 보정해도 0이고 돌릴 때마다 0이다")
    void capAlreadyReached_restoresNothing() {
        rewards.onRunSaved(event("r-full", 20_000, YESTERDAY)); // 전날 200
        saved.add(run("r-full", ALICE, 20_000, YESTERDAY));
        saved.add(run("r-over", ALICE, 5_000, YESTERDAY.plusHours(1)));

        assertThat(reconcile.reconcile(NOW)).isZero();
        assertThat(reconcile.reconcile(NOW)).isZero();
        assertThat(rewards.accruedFor(AccrualReason.VALID_RUN, "r-over", ALICE)).isZero();
        assertThat(rewards.balanceOf(ALICE)).isEqualTo(200);
    }

    @Test
    @DisplayName("빠진 대결 승리 적립을 판정 시각으로 채우고, 무효 기록·탈퇴 회원은 건너뛴다")
    void restoresBattleWin_skipsInvalidAndWithdrawn() {
        wins.add(new BattleWin(9, ALICE, YESTERDAY));
        wins.add(new BattleWin(10, WITHDRAWN, YESTERDAY));
        saved.add(
                new SavedRun(
                        "r-invalid",
                        ALICE,
                        YESTERDAY,
                        Distance.ofMeters(5_000),
                        false,
                        null,
                        YESTERDAY));

        assertThat(reconcile.reconcile(NOW)).isEqualTo(1);
        assertThat(rewards.accruedFor(AccrualReason.BATTLE_WIN, "9", ALICE)).isEqualTo(20);
        assertThat(rewards.balanceOf(WITHDRAWN)).isZero();
        assertThat(wallets.findByMember(WITHDRAWN)).isEmpty();
        assertThat(rewards.accruedFor(AccrualReason.VALID_RUN, "r-invalid", ALICE)).isZero();
    }

    @Test
    @DisplayName("규칙상 적립량이 0인 짧은 유효 기록(km당 10에 90m)은 지갑을 잠그지 않고 건너뛴다(B-L2)")
    void zeroAmountRun_doesNotTouchWallet() {
        saved.add(run("r-short", ALICE, 90, YESTERDAY)); // ⌊0.09 km × 10⌋ = 0
        wallets.failNext(new IllegalStateException("지갑을 잠그면 안 된다"));

        assertThat(reconcile.reconcile(NOW)).isZero();
        assertThat(wallets.findByMember(ALICE)).isEmpty();
        saved.add(run("r-ok", ALICE, 3_000, YESTERDAY));
        assertThat(reconcile.reconcile(NOW)).isZero(); // 남겨 둔 실패를 이번에 처음 잠근 r-ok가 받는다
        assertThat(reconcile.reconcile(NOW)).isEqualTo(1);
    }

    @Test
    @DisplayName("승리 적립이 0으로 바뀌면 대결 판정을 읽지 않는다")
    void zeroBattleWin_skipsJudgedBattles() {
        when(policy.rewardRule()).thenReturn(new RewardRule(10, 0, 200));
        wins.add(new BattleWin(9, ALICE, YESTERDAY));

        assertThat(reconcile.reconcile(NOW)).isZero();
        assertThat(wallets.findByMember(ALICE)).isEmpty();
    }

    @Test
    @DisplayName("보정 중 한 건이 실패해도 다음 근거로 넘어간다")
    void oneFailure_doesNotStopOthers() {
        saved.add(run("r-1", ALICE, 3_000, YESTERDAY));
        saved.add(run("r-2", ALICE, 4_000, YESTERDAY));
        wallets.failNext(new IllegalStateException("db down"));

        assertThat(reconcile.reconcile(NOW)).isEqualTo(1);
        assertThat(reconcile.reconcile(NOW)).isEqualTo(1);
        assertThat(rewards.balanceOf(ALICE)).isEqualTo(70);
    }

    @Test
    @DisplayName("TC-6792 지갑 칼럼이 오늘(30)일 때 전날 보정은 sumOn(전날) = 100으로 상한을 세고 칼럼은 그대로다")
    void olderDayReconcile_usesSumOn_keepsColumns() {
        rewards.onRunSaved(event("r-today", 3_000, NOW)); // 오늘 30
        accruals.seed(new RewardAccrual(ALICE, AccrualReason.VALID_RUN, "r-prev", 100, YESTERDAY));
        saved.add(run("r-missed", ALICE, 5_000, YESTERDAY.plusHours(1)));

        assertThat(reconcile.reconcile(NOW)).isEqualTo(1);
        assertThat(rewards.accruedFor(AccrualReason.VALID_RUN, "r-missed", ALICE)).isEqualTo(50);
        assertThat(accruals.sumOnDays()).containsExactly(YESTERDAY.toLocalDate());
        assertThat(wallets.findByMember(ALICE).orElseThrow().accruedOn(NOW.toLocalDate()))
                .hasValue(30);
    }

    /** 트랜잭션 없이 바로 실행한다. */
    private static final class DirectTx implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
    }
}
