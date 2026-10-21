package com.a207.runkk.battle;

import static com.a207.runkk.battle.BattleFixtures.ALICE;
import static com.a207.runkk.battle.BattleFixtures.BOB;
import static com.a207.runkk.battle.BattleFixtures.NOW;
import static com.a207.runkk.battle.BattleFixtures.spec;
import static com.a207.runkk.battle.BattleFixtures.startOf;
import static com.a207.runkk.battle.BattleFixtures.startedRandom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.StandardDistance;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Battle 상태도 2.4.2의 전이와 금지 전이, srs.md 6.3 4.5 판정 의사결정표 4규칙 + 보류·정정(14장 단위). */
@DisplayName("Battle 상태 전이와 판정")
class BattleTest {

    private static final Duration ALICE_REACH = Duration.ofMinutes(25);
    private static final Duration BOB_REACH = Duration.ofMinutes(25).plusSeconds(5);

    private static Side alice() {
        return Side.member(ALICE);
    }

    private static Side bob() {
        return Side.member(BOB);
    }

    /** 앨리스가 먼저 도착해 카운트다운이 끝난 랜덤 대결(RESULT_PENDING). */
    private static Battle aliceArrivedFirstAndClosed(Duration bobReach) {
        Battle battle = startedRandom();
        LocalDateTime start = startOf(battle);
        battle.recordArrival(alice(), start.plus(ALICE_REACH));
        if (bobReach != null) {
            battle.recordArrival(bob(), start.plus(bobReach));
        }
        battle.closeCountdown(battle.getCountdownEndsAt().orElseThrow());
        return battle;
    }

    @Nested
    @DisplayName("상태 전이")
    class Transitions {

        @Test
        @DisplayName("random: 배정된 두 표 → WAITING_START, 배정 전 표는 거부")
        void random_needsAssignedTickets() {
            Battle battle = startedRandom();
            assertThat(battle.getStatus()).isEqualTo(BattleStatus.IN_PROGRESS);
            assertThat(battle.memberIds()).containsExactly(ALICE, BOB);

            MatchTicket waiting =
                    MatchTicket.open(
                            3L, com.a207.runkk.common.Tier.GOLD, StandardDistance.KM5, NOW);
            assertThatThrownBy(
                            () ->
                                    Battle.random(
                                            waiting,
                                            BattleFixtures.assigned(BOB, StandardDistance.KM5),
                                            NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("start: startAt 전이면 거부, 지나면 IN_PROGRESS")
        void start_waitsForStartAt() {
            Battle battle =
                    Battle.random(
                            BattleFixtures.assigned(ALICE, StandardDistance.KM5),
                            BattleFixtures.assigned(BOB, StandardDistance.KM5),
                            NOW);
            assertThatThrownBy(() -> battle.start(NOW)).isInstanceOf(IllegalStateException.class);
            battle.scheduleStart(NOW.plusSeconds(13));
            assertThatThrownBy(() -> battle.start(NOW.plusSeconds(12)))
                    .isInstanceOf(IllegalStateException.class);
            battle.start(NOW.plusSeconds(13));
            assertThat(battle.getStatus()).isEqualTo(BattleStatus.IN_PROGRESS);
            assertThatThrownBy(() -> battle.scheduleStart(NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName(
                "againstRecord: GHOST는 WAITING_START, SELF는 IN_PROGRESS이고 시작 보고 때 상대 도착 시각이 정해진다")
        void againstRecord_initialStatesAndStartReport() {
            Battle ghost = Battle.againstRecord(spec(BattleType.GHOST, ALICE_REACH), NOW);
            assertThat(ghost.getStatus()).isEqualTo(BattleStatus.WAITING_START);

            Battle self = Battle.againstRecord(spec(BattleType.SELF, ALICE_REACH), NOW);
            assertThat(self.getStatus()).isEqualTo(BattleStatus.IN_PROGRESS);
            assertThat(self.getStartAt()).isEmpty();
            self.start(NOW.plusSeconds(5));
            assertThat(self.getOpponentArrivesAt()).contains(NOW.plusSeconds(5).plus(ALICE_REACH));
            assertThatThrownBy(() -> self.start(NOW.plusSeconds(9)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(self.getOpponentTrack()).isPresent();
        }

        @Test
        @DisplayName("recordArrival: 첫 도착은 COUNTDOWN(+10초), 카운트다운 안은 기록만, 뒤는 무시")
        void recordArrival_countdown() {
            Battle battle = startedRandom();
            LocalDateTime start = startOf(battle);
            assertThat(battle.recordArrival(alice(), start.plus(ALICE_REACH))).isTrue();
            assertThat(battle.getStatus()).isEqualTo(BattleStatus.COUNTDOWN);
            assertThat(battle.getCountdownEndsAt())
                    .contains(start.plus(ALICE_REACH).plusSeconds(10));
            assertThat(battle.recordArrival(alice(), start.plus(ALICE_REACH))).isFalse();
            assertThat(battle.recordArrival(bob(), start.plus(ALICE_REACH).plusSeconds(11)))
                    .isFalse();
            assertThat(battle.participant(BOB).orElseThrow().getArrivedAt()).isEmpty();
            assertThat(battle.recordArrival(bob(), start.plus(ALICE_REACH).plusSeconds(5)))
                    .isFalse();
            assertThat(battle.participant(BOB).orElseThrow().getArrivedAt()).isPresent();
        }

        @Test
        @DisplayName("closeCountdown: 카운트다운 전이면 거부, 끝나면 RESULT_PENDING, 최대 시간(거리 × 15분)도 닫는다")
        void closeCountdown_whenDue() {
            Battle battle = startedRandom();
            LocalDateTime start = startOf(battle);
            assertThatThrownBy(() -> battle.closeCountdown(start.plusMinutes(10)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(battle.isCloseDue(start.plusMinutes(75))).isTrue();
            battle.closeCountdown(start.plusMinutes(75));
            assertThat(battle.getStatus()).isEqualTo(BattleStatus.RESULT_PENDING);
        }

        @Test
        @DisplayName("끊김·복구는 참가자의 끊김 기록을 열고 닫는다")
        void disconnectAndReconnect() {
            Battle battle = startedRandom();
            battle.markDisconnected(ALICE, NOW.plusMinutes(1));
            battle.markDisconnected(ALICE, NOW.plusMinutes(2));
            battle.markReconnected(ALICE, NOW.plusMinutes(3));
            BattleParticipant alice = battle.participant(ALICE).orElseThrow();
            assertThat(alice.getDisconnections()).hasSize(1);
            assertThat(alice.getDisconnections().get(0).getReconnectedAt())
                    .isEqualTo(NOW.plusMinutes(3));
            assertThatThrownBy(() -> battle.markDisconnected(99L, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("forfeit: 포기자 패, 상대 승, FINISHED. 끝난 대결은 포기할 수 없다")
        void forfeit_losesAndFinishes() {
            Battle battle = startedRandom();
            battle.forfeit(ALICE, NOW.plusMinutes(5));
            Judgement judgement = battle.getJudgement().orElseThrow();
            assertThat(judgement.verdictOf(alice())).contains(Verdict.LOSE);
            assertThat(judgement.verdictOf(bob())).contains(Verdict.WIN);
            assertThat(judgement.winnerSide()).contains(bob());
            assertThat(battle.getStatus()).isEqualTo(BattleStatus.FINISHED);
            assertThat(battle.judge(NOW.plusMinutes(6))).isEmpty();
            assertThatThrownBy(() -> battle.forfeit(BOB, NOW.plusMinutes(6)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("판정 의사결정표")
    class DecisionTable {

        @Test
        @DisplayName("규칙 1: 1초 이내 도착 + 둘 다 유효 → 무승부")
        void rule1_withinOneSecond_draw() {
            Battle battle = aliceArrivedFirstAndClosed(ALICE_REACH.plusMillis(900));
            battle.verify(ALICE, "a", true, ALICE_REACH);
            battle.verify(BOB, "b", true, ALICE_REACH.plusMillis(900));
            Judgement judgement = battle.judge(NOW.plusHours(1)).orElseThrow();
            assertThat(judgement.verdicts().values()).containsOnly(Verdict.DRAW);
            assertThat(judgement.winnerSide()).isEmpty();
        }

        @Test
        @DisplayName("규칙 2: 먼저 도착한 쪽 유효 → 그쪽 승, 카운트다운 안에 못 온 쪽 패")
        void rule2_firstValid_wins() {
            Battle battle = aliceArrivedFirstAndClosed(null);
            battle.verify(ALICE, "a", true, ALICE_REACH);
            battle.verify(BOB, "b", true, null);
            Judgement judgement = battle.judge(NOW.plusHours(1)).orElseThrow();
            assertThat(judgement.verdictOf(alice())).contains(Verdict.WIN);
            assertThat(judgement.verdictOf(bob())).contains(Verdict.LOSE);
            assertThat(judgement.corrected()).isFalse();
            assertThat(battle.getStatus()).isEqualTo(BattleStatus.FINISHED);
        }

        @Test
        @DisplayName("규칙 3: 먼저 도착한 쪽 무효 → 그쪽 패, 다른 쪽이 유효하게 도착했으면 승")
        void rule3_firstInvalid_otherWins() {
            Battle battle = aliceArrivedFirstAndClosed(BOB_REACH);
            battle.verify(ALICE, "a", false, ALICE_REACH);
            battle.verify(BOB, "b", true, BOB_REACH);
            Judgement judgement = battle.judge(NOW.plusHours(1)).orElseThrow();
            assertThat(judgement.verdictOf(alice())).contains(Verdict.LOSE);
            assertThat(judgement.verdictOf(bob())).contains(Verdict.WIN);
        }

        @Test
        @DisplayName("규칙 3: 먼저 도착한 쪽 무효, 다른 쪽도 유효 도착 없음 → 먼저 온 쪽 패, 다른 쪽 무승부")
        void rule3_firstInvalid_noOtherValid() {
            Battle battle = aliceArrivedFirstAndClosed(null);
            battle.verify(ALICE, "a", false, ALICE_REACH);
            battle.verify(BOB, "b", true, null);
            Judgement judgement = battle.judge(NOW.plusHours(1)).orElseThrow();
            assertThat(judgement.verdictOf(alice())).contains(Verdict.LOSE);
            assertThat(judgement.verdictOf(bob())).contains(Verdict.DRAW);
        }

        @Test
        @DisplayName("규칙 4: 아무도 도착하지 않은 채 두 러닝이 끝남 → 무승부(IN_PROGRESS에서도 판정)")
        void rule4_nobodyArrived_draw() {
            Battle battle = startedRandom();
            battle.verify(ALICE, "a", true, null);
            assertThat(battle.judge(NOW.plusMinutes(30))).isEmpty();
            battle.verify(BOB, "b", true, null);
            Judgement judgement = battle.judge(NOW.plusMinutes(31)).orElseThrow();
            assertThat(judgement.verdicts().values()).containsOnly(Verdict.DRAW);
            assertThat(battle.getStatus()).isEqualTo(BattleStatus.FINISHED);
        }

        @Test
        @DisplayName("판정 도착 시각은 startAt + 기록상 도달 시간이다(D-30): 실시간 도착 순서와 달라도 기록이 이긴다")
        void arrivalUsesRecordedReach() {
            Battle battle = aliceArrivedFirstAndClosed(ALICE_REACH.plusSeconds(3));
            battle.verify(ALICE, "a", true, ALICE_REACH.plusSeconds(5));
            battle.verify(BOB, "b", true, ALICE_REACH.plusSeconds(3));
            Judgement judgement = battle.judge(NOW.plusHours(1)).orElseThrow();
            assertThat(judgement.verdictOf(bob())).contains(Verdict.WIN);
        }
    }

    @Nested
    @DisplayName("보류·정정·만료")
    class PendingAndCorrection {

        @Test
        @DisplayName("먼저 도착한 쪽 기록이 없으면 보류(빈 판정), 기록이 오면 판정")
        void pending_untilFirstRecordArrives() {
            Battle battle = aliceArrivedFirstAndClosed(null);
            battle.verify(BOB, "b", true, null);
            assertThat(battle.judge(NOW.plusHours(1))).isEmpty();
            assertThat(battle.getStatus()).isEqualTo(BattleStatus.RESULT_PENDING);
            battle.verify(ALICE, "a", true, ALICE_REACH);
            assertThat(battle.judge(NOW.plusHours(2))).isPresent();
        }

        @Test
        @DisplayName("진행 중에 아직 달리는 참가자의 기록이 없으면 판정하지 않는다")
        void inProgress_waitsForRunningParticipant() {
            Battle battle = startedRandom();
            battle.recordArrival(alice(), startOf(battle).plus(ALICE_REACH));
            battle.verify(ALICE, "a", true, ALICE_REACH);
            assertThat(battle.judge(NOW.plusMinutes(30))).isEmpty();
        }

        @Test
        @DisplayName("정정: 끝난 판정이 먼저 도착한 쪽 무효로 바뀌면 corrected = true")
        void correction_marksCorrected() {
            Battle battle = aliceArrivedFirstAndClosed(BOB_REACH);
            battle.verify(ALICE, "a", true, ALICE_REACH);
            battle.verify(BOB, "b", true, BOB_REACH);
            assertThat(battle.judge(NOW.plusHours(1)).orElseThrow().corrected()).isFalse();

            battle.verify(ALICE, "a", false, ALICE_REACH);
            Judgement corrected = battle.judge(NOW.plusHours(2)).orElseThrow();
            assertThat(corrected.corrected()).isTrue();
            assertThat(corrected.verdictOf(bob())).contains(Verdict.WIN);

            Judgement same = battle.judge(NOW.plusHours(3)).orElseThrow();
            assertThat(same.corrected()).isFalse();
        }

        @Test
        @DisplayName("expirePending: 24시간 전이면 비어 있고, 지나면 없는 기록을 무효로 보고 판정(정정 아님)")
        void expirePending_after24Hours() {
            Battle battle = aliceArrivedFirstAndClosed(BOB_REACH);
            battle.verify(BOB, "b", true, BOB_REACH);
            LocalDateTime endsAt = battle.getCountdownEndsAt().orElseThrow();
            assertThat(battle.expirePending(endsAt.plusHours(23))).isEmpty();

            Optional<Judgement> expired = battle.expirePending(endsAt.plusHours(24));
            assertThat(expired).isPresent();
            assertThat(expired.get().verdictOf(alice())).contains(Verdict.LOSE);
            assertThat(expired.get().verdictOf(bob())).contains(Verdict.WIN);
            assertThat(expired.get().corrected()).isFalse();
            assertThat(battle.expirePending(endsAt.plusHours(25))).isEmpty();
        }
    }

    @Nested
    @DisplayName("기록 대결")
    class RecordBattle {

        @Test
        @DisplayName("기록 상대 도착 → COUNTDOWN, 러너가 먼저 도달한 기록이면 러너 승")
        void runnerBeatsRecord() {
            Battle battle = Battle.againstRecord(spec(BattleType.SELF, BOB_REACH), NOW);
            battle.start(NOW);
            assertThat(battle.isOpponentArrivalDue(NOW.plus(BOB_REACH))).isTrue();
            assertThat(battle.recordArrival(Side.OPPONENT, NOW.plus(BOB_REACH))).isTrue();
            assertThat(battle.isOpponentArrivalDue(NOW.plus(BOB_REACH))).isFalse();
            battle.verify(ALICE, "a", true, ALICE_REACH);
            Judgement judgement = battle.judge(NOW.plus(BOB_REACH).plusSeconds(2)).orElseThrow();
            assertThat(judgement.verdictOf(alice())).contains(Verdict.WIN);
            assertThat(judgement.verdictOf(Side.OPPONENT)).contains(Verdict.LOSE);
        }

        @Test
        @DisplayName("러너 기록이 없으면 닫힌 뒤에도 보류하고, 24시간 뒤 기록 상대 승")
        void recordBattle_pendingUntilRunnerRecord() {
            Battle battle = Battle.againstRecord(spec(BattleType.CELEBRITY, ALICE_REACH), NOW);
            battle.start(NOW);
            battle.recordArrival(Side.OPPONENT, NOW.plus(ALICE_REACH));
            LocalDateTime endsAt = battle.getCountdownEndsAt().orElseThrow();
            battle.closeCountdown(endsAt);
            assertThat(battle.judge(endsAt)).isEmpty();
            Judgement judgement = battle.expirePending(endsAt.plusHours(24)).orElseThrow();
            assertThat(judgement.winnerSide()).contains(Side.OPPONENT);
        }

        @Test
        @DisplayName("기록 상대에게도 포기하면 기록 상대 승")
        void forfeitAgainstRecord() {
            Battle battle = Battle.againstRecord(spec(BattleType.GHOST, ALICE_REACH), NOW);
            battle.scheduleStart(NOW.plusSeconds(13));
            battle.start(NOW.plusSeconds(13));
            battle.forfeit(ALICE, NOW.plusMinutes(1));
            assertThat(battle.getJudgement().orElseThrow().winnerSide()).contains(Side.OPPONENT);
        }
    }
}
