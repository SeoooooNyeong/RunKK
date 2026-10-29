package com.a207.runkk.battle;

import static com.a207.runkk.battle.BattleFixtures.ALICE;
import static com.a207.runkk.battle.BattleFixtures.BOB;
import static com.a207.runkk.battle.BattleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunRecordDeleted;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * v0.14 대결 변경(D-225~D-230, D-232): BattleType FRIEND 다형 메소드,
 * Battle.friend·cancelOnForfeit·forgetRecord, 셀프런·셀럽런 진행 중 포기 = 취소, 이력 필터 묶음, 기록 목록 대결
 * 결과(outcomesOf). test.md 12.2.3 TC-9291~TC-9317, 기초 경로 TC-9521~TC-9524.
 */
@DisplayName("v0.14 대결 종류·포기·이력")
class BattleV014Test {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("TC-9291~TC-9298 BattleType 상수 다섯 개의 다형 메소드")
    void tc9291_battleTypeMethods() {
        assertThat(BattleType.values())
                .containsExactly(
                        BattleType.RANDOM,
                        BattleType.GHOST,
                        BattleType.FRIEND,
                        BattleType.SELF,
                        BattleType.CELEBRITY);
        assertThat(BattleType.values())
                .filteredOn(BattleType::hasLiveRival)
                .containsExactly(BattleType.RANDOM, BattleType.FRIEND);
        for (BattleType t : BattleType.values()) {
            assertThat(t.isAgainstRecord()).isEqualTo(!t.hasLiveRival());
        }
        assertThat(BattleType.GHOST.historyGroup()).isEqualTo(BattleType.RANDOM);
        assertThat(BattleType.FRIEND.historyGroup()).isEqualTo(BattleType.FRIEND);
        assertThat(BattleType.SELF.historyGroup()).isEqualTo(BattleType.SELF);
        assertThat(BattleType.CELEBRITY.historyGroup()).isEqualTo(BattleType.CELEBRITY);
        assertThat(BattleType.RANDOM.filterGroup()).isEqualTo(BattleType.RANDOM);
        assertThat(BattleType.GHOST.filterGroup()).isEqualTo(BattleType.RANDOM);
        assertThat(BattleType.FRIEND.filterGroup()).isEqualTo(BattleType.RANDOM);
        assertThat(BattleType.SELF.filterGroup()).isEqualTo(BattleType.SELF);
        assertThat(BattleType.CELEBRITY.filterGroup()).isEqualTo(BattleType.CELEBRITY);
        assertThat(BattleType.values())
                .filteredOn(t -> !t.rewardsWin())
                .containsExactly(BattleType.FRIEND);
        assertThat(BattleType.winRewarded())
                .containsExactlyInAnyOrder(
                        BattleType.RANDOM, BattleType.GHOST, BattleType.SELF, BattleType.CELEBRITY);
        assertThat(BattleType.values())
                .filteredOn(BattleType::cancelsOnForfeit)
                .containsExactly(BattleType.SELF, BattleType.CELEBRITY);
        assertThat(BattleType.SELF.comparisonItems())
                .containsExactly(ComparisonItem.RECORD_TIME, ComparisonItem.AVERAGE_PACE);
        for (BattleType t :
                List.of(
                        BattleType.RANDOM,
                        BattleType.GHOST,
                        BattleType.FRIEND,
                        BattleType.CELEBRITY)) {
            assertThat(t.comparisonItems()).as(t.name()).isEmpty();
        }
        assertThat(BattleType.FRIEND.startsWithCountdown()).isFalse();
        assertThat(BattleType.SELF.startsWithCountdown()).isTrue();
        assertThat(BattleType.RANDOM.startsWithCountdown()).isFalse();
    }

    @Test
    @DisplayName("TC-9299 모든 상수가 다형 메소드 일곱 개를 예외 없이 답한다(반사)")
    void tc9299_reflection() throws Exception {
        List<String> names =
                List.of(
                        "hasLiveRival",
                        "isAgainstRecord",
                        "historyGroup",
                        "filterGroup",
                        "rewardsWin",
                        "cancelsOnForfeit",
                        "comparisonItems");
        for (BattleType t : BattleType.values()) {
            for (String name : names) {
                Method m = BattleType.class.getMethod(name);
                assertThat(m.invoke(t)).as(t + "." + name).isNotNull();
            }
        }
    }

    private static FriendInvite accepted() {
        FriendInvite invite = FriendInvite.open(ALICE, StandardDistance.KM5, NOW);
        invite.accept(BOB, NOW.plusSeconds(30));
        return invite;
    }

    @Test
    @DisplayName("TC-9301·TC-9302 Battle.friend: ACCEPTED 초대만, FRIEND·WAITING_START·참가자 둘·초대 거리")
    void tc9301_friendBattle() {
        Battle battle = Battle.friend(accepted(), NOW);
        assertThat(battle.getType()).isEqualTo(BattleType.FRIEND);
        assertThat(battle.getStatus()).isEqualTo(BattleStatus.WAITING_START);
        assertThat(battle.memberIds()).containsExactly(ALICE, BOB);
        assertThat(battle.getDistance().meters()).isEqualTo(5_000);
        assertThat(battle.getOpponent()).isEmpty();

        FriendInvite pending = FriendInvite.open(ALICE, StandardDistance.KM5, NOW);
        assertThatThrownBy(() -> Battle.friend(pending, NOW))
                .isInstanceOf(IllegalStateException.class);
        FriendInvite noJoiner = FriendInvite.open(ALICE, StandardDistance.KM5, NOW);
        FriendInviteTest.forceStatus(noJoiner, FriendInviteStatus.ACCEPTED);
        assertThatThrownBy(() -> Battle.friend(noJoiner, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Battle startedFriend() {
        Battle battle = Battle.friend(accepted(), NOW);
        battle.scheduleStart(NOW.plusSeconds(13));
        battle.start(NOW.plusSeconds(13));
        return battle;
    }

    private static Battle reported(BattleType type) {
        Battle battle =
                Battle.againstRecord(BattleFixtures.spec(type, Duration.ofMinutes(25)), NOW);
        battle.start(NOW.plusSeconds(3));
        return battle;
    }

    @Test
    @DisplayName(
            "TC-9303~TC-9309·TC-9521~TC-9524 cancelOnForfeit: 출발 전 모두 취소, 셀프·셀럽 진행 중 취소, 랜덤·고스트·친구"
                    + " 진행 중은 거짓")
    void tc9303_cancelOnForfeit() {
        Battle waiting =
                Battle.random(
                        BattleFixtures.assigned(ALICE, StandardDistance.KM5),
                        BattleFixtures.assigned(BOB, StandardDistance.KM5),
                        NOW);
        assertThat(waiting.cancelOnForfeit(NOW.plusSeconds(5))).isTrue();
        assertThat(waiting.getStatus()).isEqualTo(BattleStatus.CANCELLED);
        assertThat(waiting.getParticipants()).allMatch(p -> !waiting.isActive());

        Battle random = BattleFixtures.startedRandom();
        assertThat(random.cancelOnForfeit(NOW.plusMinutes(1))).isFalse();
        assertThat(random.getStatus()).isEqualTo(BattleStatus.IN_PROGRESS);

        Battle friendWaiting = Battle.friend(accepted(), NOW);
        assertThat(friendWaiting.cancelOnForfeit(NOW.plusSeconds(5))).isTrue();
        Battle friendRunning = startedFriend();
        assertThat(friendRunning.cancelOnForfeit(NOW.plusMinutes(1))).isFalse();
        friendRunning.forfeit(ALICE, NOW.plusMinutes(1));
        assertThat(friendRunning.getStatus()).isEqualTo(BattleStatus.FINISHED);

        Battle self = reported(BattleType.SELF);
        assertThat(self.cancelOnForfeit(NOW.plusMinutes(2))).isTrue();
        assertThat(self.getStatus()).isEqualTo(BattleStatus.CANCELLED);
        assertThat(self.isActive()).isFalse();

        Battle celeb = reported(BattleType.CELEBRITY);
        celeb.recordArrival(Side.OPPONENT, NOW.plusMinutes(20));
        assertThat(celeb.getStatus()).isEqualTo(BattleStatus.COUNTDOWN);
        assertThat(celeb.cancelOnForfeit(NOW.plusMinutes(20).plusSeconds(1))).isTrue();
        assertThat(celeb.getStatus()).isEqualTo(BattleStatus.CANCELLED);
        assertThat(reported(BattleType.CELEBRITY).cancelOnForfeit(NOW.plusMinutes(1))).isTrue();

        Battle ghost =
                Battle.againstRecord(
                        BattleFixtures.spec(BattleType.GHOST, Duration.ofMinutes(25)), NOW);
        ghost.scheduleStart(NOW.plusSeconds(13));
        ghost.start(NOW.plusSeconds(13));
        assertThat(ghost.cancelOnForfeit(NOW.plusMinutes(1))).isFalse();

        assertThat(self.cancelOnForfeit(NOW.plusMinutes(3))).as("이미 취소").isFalse();
        Battle finished = reported(BattleType.SELF);
        finished.forfeit(ALICE, NOW.plusMinutes(1));
        assertThat(finished.cancelOnForfeit(NOW.plusMinutes(2))).isFalse();
        assertThat(finished.getStatus()).isEqualTo(BattleStatus.FINISHED);
    }

    @Test
    @DisplayName("TC-9313 RecordBattleSpec: RANDOM·FRIEND는 기록 대결이 아니다")
    void tc9313_recordBattleSpec() {
        assertThatThrownBy(() -> BattleFixtures.spec(BattleType.RANDOM, Duration.ofMinutes(20)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BattleFixtures.spec(BattleType.FRIEND, Duration.ofMinutes(20)))
                .isInstanceOf(IllegalArgumentException.class);
        for (BattleType t : List.of(BattleType.SELF, BattleType.CELEBRITY, BattleType.GHOST)) {
            assertThat(BattleFixtures.spec(t, Duration.ofMinutes(20)).type()).isEqualTo(t);
        }
    }

    @Test
    @DisplayName("D-296 BattleJudged.rewardsWin은 대결 종류가 답한다(친구 대결만 거짓)")
    void battleJudgedRewardsWin() {
        Judgement j =
                new Judgement(
                        Map.of(Side.member(ALICE), Verdict.WIN), Side.member(ALICE), false, NOW);
        assertThat(new BattleJudged(1, BattleType.FRIEND, j, NOW).rewardsWin()).isFalse();
        assertThat(new BattleJudged(1, BattleType.RANDOM, j, NOW).rewardsWin()).isTrue();
    }

    /** 서비스 묶음. */
    private final InMemoryBattleStores.Battles battles = new InMemoryBattleStores.Battles();

    private final InMemoryBattleStores.Messenger messenger = new InMemoryBattleStores.Messenger();
    private final List<DomainEvent> events = new ArrayList<>();
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final RunQueryService runs = mock(RunQueryService.class);
    private final Clock clock = Clock.fixed(NOW.plusMinutes(5).atZone(SEOUL).toInstant(), SEOUL);
    private BattleResultService results;
    private BattleHistoryService history;

    @BeforeEach
    void wire() {
        when(members.hasConsent(anyLong(), eq(ConsentItem.LOCATION))).thenReturn(true);
        when(members.summaries(anyCollection()))
                .thenAnswer(
                        inv -> {
                            Map<Long, MemberSummary> map = new HashMap<>();
                            for (Object id : (java.util.Collection<?>) inv.getArgument(0)) {
                                map.put((Long) id, BattleFixtures.summary((Long) id, Tier.GOLD));
                            }
                            return map;
                        });
        results =
                new BattleResultService(
                        battles,
                        runs,
                        members,
                        mock(NotificationService.class),
                        events::add,
                        messenger,
                        clock,
                        InMemoryBattleStores.transactions());
        history =
                new BattleHistoryService(battles, members, new InMemoryBattleStores.Celebrities());
    }

    @ParameterizedTest(name = "TC-9310 {0}")
    @EnumSource(
            value = BattleType.class,
            names = {"SELF", "CELEBRITY"})
    @DisplayName("TC-9310 셀프런·셀럽런 진행 중 포기는 취소: 판정 사건 0, 이력 0, 결과 조회 BATTLE_NOT_FOUND, 활성 칸 비움")
    void tc9310_recordForfeitCancels(BattleType type) {
        Battle battle = battles.save(reported(type));
        results.forfeit(ALICE, battle.getId());
        assertThat(battles.findById(battle.getId()).orElseThrow().getStatus())
                .isEqualTo(BattleStatus.CANCELLED);
        assertThat(events).isEmpty();
        assertThat(battles.existsActive(ALICE)).isFalse();
        assertThat(
                        history.history(
                                        ALICE,
                                        new DateRange(
                                                LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1)),
                                        Optional.empty())
                                .entries())
                .isEmpty();
        assertThatThrownBy(() -> results.result(ALICE, battle.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.BATTLE_NOT_FOUND);
    }

    @Test
    @DisplayName("TC-9311 친구 대결 진행 중 포기는 포기자 패(FINISHED), 상대에게 BattleEnded, 판정 사건의 rewardsWin 거짓")
    void tc9311_friendForfeitLoses() {
        Battle battle = battles.save(startedFriend());
        results.forfeit(ALICE, battle.getId());
        Battle done = battles.findById(battle.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(BattleStatus.FINISHED);
        assertThat(done.participant(ALICE).orElseThrow().getVerdict()).contains(Verdict.LOSE);
        assertThat(messenger.to(BOB, "BattleEnded")).hasSize(1);
        assertThat(events).hasSize(1);
        BattleJudged judged = (BattleJudged) events.get(0);
        assertThat(judged.rewardsWin()).isFalse();
        assertThat(judged.winnerSide()).contains(Side.member(BOB));
    }

    @Test
    @DisplayName("TC-9312 출발 전 포기는 종류와 상관없이 취소")
    void tc9312_unstartedForfeitCancels() {
        Battle friend = battles.save(Battle.friend(accepted(), NOW));
        results.forfeit(ALICE, friend.getId());
        assertThat(battles.findById(friend.getId()).orElseThrow().isCancelled()).isTrue();
        Battle self =
                battles.save(
                        Battle.againstRecord(
                                BattleFixtures.spec(BattleType.SELF, Duration.ofMinutes(25)), NOW));
        results.forfeit(ALICE, self.getId());
        assertThat(battles.findById(self.getId()).orElseThrow().isCancelled()).isTrue();
        assertThat(events).isEmpty();
    }

    private Battle finished(Battle battle, LocalDateTime createdAt, long loser) {
        ReflectionTestUtils.setField(battle, "createdAt", createdAt);
        Battle saved = battles.save(battle);
        if (saved.getStatus() == BattleStatus.WAITING_START) {
            saved.scheduleStart(createdAt.plusSeconds(13));
            saved.start(createdAt.plusSeconds(13));
        } else if (saved.getStartAt().isEmpty()) {
            saved.start(createdAt.plusSeconds(3));
        }
        saved.forfeit(loser, createdAt.plusMinutes(1));
        return saved;
    }

    @Test
    @DisplayName("TC-9316 이력 필터: RANDOM은 랜덤·고스트·친구, SELF·CELEBRITY 각 1, 행 type은 historyGroup")
    void tc9316_historyFilter() {
        LocalDateTime day = LocalDateTime.of(2026, 10, 20, 7, 0);
        finished(
                Battle.random(
                        BattleFixtures.assigned(ALICE, StandardDistance.KM5),
                        BattleFixtures.assigned(BOB, StandardDistance.KM5),
                        day),
                day,
                BOB);
        finished(
                Battle.againstRecord(
                        BattleFixtures.spec(BattleType.GHOST, Duration.ofMinutes(25)),
                        day.plusHours(1)),
                day.plusHours(1),
                ALICE);
        finished(Battle.friend(accepted(), day.plusHours(2)), day.plusHours(2), BOB);
        finished(
                Battle.againstRecord(
                        BattleFixtures.spec(BattleType.SELF, Duration.ofMinutes(25)),
                        day.plusHours(3)),
                day.plusHours(3),
                ALICE);
        finished(
                Battle.againstRecord(
                        BattleFixtures.spec(BattleType.CELEBRITY, Duration.ofMinutes(25)),
                        day.plusHours(4)),
                day.plusHours(4),
                ALICE);
        DateRange range = new DateRange(day.toLocalDate(), day.toLocalDate());

        BattleHistoryView all = history.history(ALICE, range, Optional.empty());
        assertThat(all.entries())
                .extracting(BattleHistoryView.Entry::type)
                .containsExactly(
                        BattleType.CELEBRITY,
                        BattleType.SELF,
                        BattleType.FRIEND,
                        BattleType.RANDOM,
                        BattleType.RANDOM);
        BattleHistoryView runs = history.history(ALICE, range, Optional.of(BattleType.RANDOM));
        assertThat(runs.entries()).hasSize(3);
        assertThat(runs.total()).isEqualTo(3);
        assertThat(runs.wins()).isEqualTo(2);
        assertThat(history.history(ALICE, range, Optional.of(BattleType.SELF)).entries())
                .hasSize(1);
        assertThat(history.history(ALICE, range, Optional.of(BattleType.CELEBRITY)).entries())
                .hasSize(1);
    }

    @Test
    @DisplayName("TC-9317 outcomesOf: FINISHED 판정, RESULT_PENDING 보류, 취소·없는 ID 제외, 한 번 조회")
    void tc9317_outcomesOf() {
        Battle won = finished(Battle.friend(accepted(), NOW), NOW, BOB);
        Battle lost =
                finished(
                        Battle.againstRecord(
                                BattleFixtures.spec(BattleType.GHOST, Duration.ofMinutes(25)), NOW),
                        NOW,
                        ALICE);
        Battle pending = battles.save(reported(BattleType.SELF));
        pending.closeCountdown(NOW.plusHours(2));
        Battle cancelled = battles.save(Battle.friend(accepted(), NOW));
        cancelled.cancelOnForfeit(NOW);

        Map<Long, BattleOutcome> outcomes =
                history.outcomesOf(
                        ALICE,
                        List.of(
                                won.getId(),
                                lost.getId(),
                                pending.getId(),
                                cancelled.getId(),
                                999L));

        assertThat(battles.findAllByIdCalls).isEqualTo(1);
        assertThat(outcomes).containsOnlyKeys(won.getId(), lost.getId(), pending.getId());
        assertThat(outcomes.get(won.getId()))
                .isEqualTo(new BattleOutcome(BattleType.FRIEND, Verdict.WIN, false));
        assertThat(outcomes.get(lost.getId()))
                .isEqualTo(new BattleOutcome(BattleType.RANDOM, Verdict.LOSE, false));
        assertThat(outcomes.get(pending.getId()))
                .isEqualTo(new BattleOutcome(BattleType.SELF, null, true));
        assertThat(history.outcomesOf(ALICE, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("TC-9358 기록 삭제 사건: 대결 ID가 있으면 그 참가자의 기록 ID만 비운다")
    void tc9358_onRunDeleted() {
        Battle battle = battles.save(startedFriend());
        battle.verify(ALICE, "r-a", false, null);
        battle.verify(BOB, "r-b", true, Duration.ofMinutes(25));
        results.onRunDeleted(new RunRecordDeleted("r-a", ALICE, battle.getId(), null, NOW));
        assertThat(battle.participant(ALICE).orElseThrow().getRecordId()).isEmpty();
        assertThat(battle.participant(BOB).orElseThrow().getRecordId()).contains("r-b");
        results.onRunDeleted(new RunRecordDeleted("r-x", ALICE, null, null, NOW));
    }
}
