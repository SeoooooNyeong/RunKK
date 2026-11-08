package com.a207.runkk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.NoTransactions;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.audit.InMemoryAuditRepository;
import com.a207.runkk.battle.BattleHistoryService;
import com.a207.runkk.battle.BattleJudged;
import com.a207.runkk.battle.BattleType;
import com.a207.runkk.battle.CelebrityChallenge;
import com.a207.runkk.battle.Judgement;
import com.a207.runkk.battle.Side;
import com.a207.runkk.battle.Verdict;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.RewardRule;
import com.a207.runkk.reward.AccrualReason;
import com.a207.runkk.reward.InMemoryRewardAccrualRepository;
import com.a207.runkk.reward.InMemoryRewardWalletRepository;
import com.a207.runkk.reward.RewardService;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 코치 승리 포인트(S15P31A207-275): 셀럽마다 한 번만, 하루 상한 밖, 기간과 종류를 지키고 실패해도 던지지 않는다. */
@DisplayName("event.CoachWinPointListener")
class CoachWinPointListenerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 8, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 16, 23, 59, 59);
    private static final long MEMBER = 7L;

    private final InMemoryEventRepository events = new InMemoryEventRepository();
    private final BattleHistoryService battles = mock(BattleHistoryService.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final InMemoryRewardWalletRepository wallets = new InMemoryRewardWalletRepository();
    private final InMemoryRewardAccrualRepository accruals = new InMemoryRewardAccrualRepository();
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private final RewardService rewards =
            new RewardService(wallets, accruals, policy, NoTransactions.manager());
    private final CoachWinPointListener listener =
            new CoachWinPointListener(events, battles, rewards, clock, NoTransactions.manager());
    private final EventService service =
            new EventService(
                    events,
                    battles,
                    mock(RunQueryService.class),
                    new AuditService(new InMemoryAuditRepository(), clock),
                    clock);

    private long eventId;

    @BeforeEach
    void coachEvent() {
        // 하루 상한 200: 이벤트 포인트 500은 상한을 받지 않는다
        when(policy.rewardRule()).thenReturn(new RewardRule(10, 20, 200));
        eventId = service.create("op", EventServiceTest.coach("coach-challenge", 1)).id();
    }

    private static BattleJudged judged(BattleType type, Side winner, LocalDateTime at) {
        return new BattleJudged(
                1L,
                type,
                new Judgement(
                        Map.of(Side.member(MEMBER), Verdict.WIN, Side.OPPONENT, Verdict.LOSE),
                        winner,
                        false,
                        at),
                at);
    }

    private void wins(LocalDateTime won11, LocalDateTime won12) {
        when(battles.celebrityChallenges(MEMBER, List.of(11L, 12L), START, END))
                .thenReturn(
                        List.of(
                                new CelebrityChallenge(11, "주세빈 코치님", won11),
                                new CelebrityChallenge(12, "김선교 코치님", won12)));
    }

    @Test
    @DisplayName("셀럽 대결 승리면 이긴 코치마다 500P를 한 번만 주고 지급 이력을 남긴다. 같은 코치를 다시 이겨도 더 주지 않는다")
    void winGrantsOncePerCoach() {
        LocalDateTime first = LocalDateTime.of(2026, 11, 9, 19, 2, 11);
        wins(first, null);

        listener.onBattleJudged(judged(BattleType.CELEBRITY, Side.member(MEMBER), first));
        listener.onBattleJudged(
                judged(BattleType.CELEBRITY, Side.member(MEMBER), first.plusHours(1)));

        assertThat(rewards.balanceOf(MEMBER)).isEqualTo(500);
        assertThat(rewards.accruedFor(AccrualReason.EVENT, eventId + ":celebrity-11", MEMBER))
                .isEqualTo(500);
        assertThat(events.grants()).containsExactly(MEMBER + "/" + eventId + "/celebrity-11");

        wins(first, first.plusDays(1));
        listener.onBattleJudged(
                judged(BattleType.CELEBRITY, Side.member(MEMBER), first.plusDays(1)));

        assertThat(rewards.balanceOf(MEMBER)).isEqualTo(1_000);
        assertThat(events.grants()).hasSize(2);
    }

    @Test
    @DisplayName("지급 이력이 빠져도 적립 근거 고유 제약으로 두 번 적립하지 않는다")
    void accrualSourceStaysUnique() {
        LocalDateTime first = LocalDateTime.of(2026, 11, 9, 19, 0);
        rewards.accrueEvent(MEMBER, eventId + ":celebrity-11", 500, first);
        wins(first, null);

        listener.onBattleJudged(judged(BattleType.CELEBRITY, Side.member(MEMBER), first));

        assertThat(rewards.balanceOf(MEMBER)).isEqualTo(500);
        // 적립은 있는데 지급 이력이 빠진 상태는 다음 판정이 이력을 채운다(design-oo 05-24 R2)
        assertThat(events.grants()).containsExactly(MEMBER + "/" + eventId + "/celebrity-11");
    }

    @Test
    @DisplayName("한 코치의 지급 이력 저장이 실패해도 다른 코치 포인트는 주고, 빠진 이력은 다음 판정이 채운다")
    void oneGrantFailureDoesNotSkipOthers() {
        LocalDateTime first = LocalDateTime.of(2026, 11, 9, 19, 0);
        wins(first, first.plusMinutes(40));
        events.failNextGrant();

        listener.onBattleJudged(
                judged(BattleType.CELEBRITY, Side.member(MEMBER), first.plusMinutes(40)));

        assertThat(rewards.balanceOf(MEMBER)).isEqualTo(1_000);
        assertThat(events.grants()).containsExactly(MEMBER + "/" + eventId + "/celebrity-12");

        listener.onBattleJudged(
                judged(BattleType.CELEBRITY, Side.member(MEMBER), first.plusHours(2)));

        assertThat(rewards.balanceOf(MEMBER)).isEqualTo(1_000);
        assertThat(events.grants()).hasSize(2);
    }

    @Test
    @DisplayName("셀럽 대결이 아니거나 상대가 이겼거나 판정이 기간 밖이면 주지 않는다")
    void otherJudgementsGrantNothing() {
        wins(LocalDateTime.of(2026, 11, 9, 8, 0), null);

        listener.onBattleJudged(judged(BattleType.SELF, Side.member(MEMBER), NOW));
        listener.onBattleJudged(judged(BattleType.CELEBRITY, Side.OPPONENT, NOW));
        listener.onBattleJudged(
                judged(BattleType.CELEBRITY, Side.member(MEMBER), END.plusSeconds(1)));

        assertThat(rewards.balanceOf(MEMBER)).isZero();
        assertThat(events.grants()).isEmpty();
    }

    @Test
    @DisplayName("비공개 이벤트는 주지 않는다")
    void hiddenEventGrantsNothing() {
        service.changePublished("op", eventId, false);
        wins(NOW, null);

        listener.onBattleJudged(judged(BattleType.CELEBRITY, Side.member(MEMBER), NOW));

        assertThat(rewards.balanceOf(MEMBER)).isZero();
    }

    @Test
    @DisplayName("조회가 실패해도 예외를 던지지 않는다(대결 결과 확정을 막지 않는다)")
    void failureIsSwallowed() {
        when(battles.celebrityChallenges(anyLong(), anyList(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(
                        () ->
                                listener.onBattleJudged(
                                        judged(BattleType.CELEBRITY, Side.member(MEMBER), NOW)))
                .doesNotThrowAnyException();
        assertThat(rewards.balanceOf(MEMBER)).isZero();
    }
}
