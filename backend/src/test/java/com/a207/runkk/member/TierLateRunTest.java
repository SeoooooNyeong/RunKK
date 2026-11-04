package com.a207.runkk.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.policy.InMemoryOperationSettingRepository;
import com.a207.runkk.policy.OperationPolicy;
import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 총괄 결정(설계 쟁점 7-3): 리스너가 순번 N에서 실패하고 N+1을 먼저 반영한 구멍을 running.TierReconcileService가 기록별 반영 표시로 찾아
 * recordLateValidRun으로 메운다. 늦은 순번은 판정하지 않고 거리(레벨)와, 최근 10회 창 안이면 점수를 더한다. 같은 순번 다시 받기의 멱등(D-37)은
 * 그대로다.
 */
@DisplayName("늦게 온 유효 러닝 반영(총괄 결정 7-3)")
class TierLateRunTest {

    private static RunScoreInput input(double score, int ordinal) {
        return new RunScoreInput(score, Distance.ofKm(5), ordinal);
    }

    @Test
    @DisplayName("창 안의 늦은 순번은 판정 없이 점수·거리를 더하고 다음 판정(10회째) 평균에 들어간다")
    void lateScoreInsideWindowCountsAtNextJudgement() {
        TierProgress progress = TierProgress.empty();
        progress.addValidRun(input(30, 1));
        progress.addValidRun(input(30, 2));
        assertThat(progress.addValidRun(input(36, 4))).isEmpty(); // 3회째 첫 배치 창 없음(D-484)

        progress.addLateValidRun(input(42, 3));
        assertThat(progress.validRunCount()).isEqualTo(4);
        assertThat(progress.totalValidDistance()).isEqualTo(Distance.ofKm(20));

        OptionalDouble tenth = OptionalDouble.empty();
        for (int i = 5; i <= 10; i++) {
            tenth = progress.addValidRun(input(30, i));
        }
        assertThat(tenth.getAsDouble()).isCloseTo(31.8, within(1e-9));
    }

    @Test
    @DisplayName("창(최근 10회)보다 오래된 늦은 순번은 거리만 더한다")
    void lateScoreOutsideWindowAddsDistanceOnly() {
        TierProgress progress = TierProgress.empty();
        for (int i = 2; i <= 12; i++) {
            progress.addValidRun(input(30, i));
        }
        progress.addLateValidRun(input(99, 1));
        assertThat(progress.totalValidDistance()).isEqualTo(Distance.ofKm(60));
        OptionalDouble twentieth = OptionalDouble.empty();
        for (int i = 13; i <= 20; i++) {
            twentieth = progress.addValidRun(input(30, i));
        }
        assertThat(twentieth).hasValue(30.0);
    }

    @Test
    @DisplayName("아직 오지 않은 순번은 늦은 반영이 아니다(IllegalArgumentException)")
    void futureOrdinalIsRejected() {
        TierProgress progress = TierProgress.empty();
        progress.addValidRun(input(30, 1));
        assertThatThrownBy(() -> progress.addLateValidRun(input(30, 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TierLevelService.recordLateValidRun: 티어 그대로, 누적 거리로 레벨 갱신 뒤 저장")
    void serviceKeepsTierAndUpdatesLevel() {
        InMemoryMemberRepository members = new InMemoryMemberRepository();
        long id = members.save(MemberFixtures.member("늦은곰")).getId();
        TierLevelService service =
                new TierLevelService(
                        members,
                        new InMemoryPlacementProgressRepository(),
                        new OperationPolicy(new InMemoryOperationSettingRepository()));
        for (int i = 2; i <= 10; i++) {
            service.recordValidRun(id, new RunScoreInput(41, Distance.ofKm(5), i));
        }
        Tier tier = members.findById(id).orElseThrow().getTier();
        int level = members.findById(id).orElseThrow().getLevel();

        TierChange change =
                service.recordLateValidRun(id, new RunScoreInput(20, Distance.ofKm(5), 1));

        assertThat(change.tierChanged()).isFalse();
        assertThat(members.findById(id).orElseThrow().getTier()).isEqualTo(tier);
        assertThat(members.findById(id).orElseThrow().getLevel()).isEqualTo(level + 1);
        assertThat(members.findById(id).orElseThrow().appliedValidRunCount()).isEqualTo(10);
    }
}
