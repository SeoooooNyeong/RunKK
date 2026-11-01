package com.a207.runkk.running;

import static com.a207.runkk.running.PauseJudgementTest.free;
import static com.a207.runkk.running.PauseJudgementTest.leg;
import static com.a207.runkk.running.PauseJudgementTest.t;
import static com.a207.runkk.running.RunFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RunQueryService.curveOf(코드 리뷰 12차 BE12-5, BE12-7): curve와 같은 표본을 예외 없이 준다. 기록이 없거나 경로가 파기되었으면 비어
 * 있다. 읽기 트랜잭션 안에서 업무 예외를 잡지 않고 건너뛸 기록을 거르는 데 쓴다(워치 상대 기록 미리 받기, D-377).
 */
@DisplayName("RunQueryService.curveOf: 예외 없는 상대 표본")
class RunCurveOfTest {

    private static final double V = 5_000 / 1_800.0;

    private final InMemoryRunningRepositories.Records records =
            new InMemoryRunningRepositories.Records();
    private final RunQueryService query =
            new RunQueryService(records, new InMemoryRunningRepositories.Books(), null);

    private RunRecord stored(int n) {
        RunRecord r =
                RunRecord.receive(
                        1L,
                        free(
                                id(n),
                                t(7, 0, 0),
                                t(7, 30, 0),
                                5_000,
                                Duration.ofMinutes(30),
                                leg(t(7, 0, 0), t(7, 30, 0), 0, V),
                                List.of()));
        r.judgeValidity(ValidityRule.STANDARD);
        return records.save(r);
    }

    @Test
    @DisplayName("경로가 남은 기록은 curve와 같은 표본")
    void live_isSameAsCurve() {
        stored(1);

        assertThat(query.curveOf(id(1), Distance.ofKm(5)))
                .contains(query.curve(id(1), Distance.ofKm(5)));
    }

    @Test
    @DisplayName("기록이 없거나 경로가 파기되었으면 비어 있다")
    void missingOrPurged_isEmpty() {
        stored(2).purgeRoute();

        assertThat(query.curveOf(id(2), Distance.ofKm(5))).isEmpty();
        assertThat(query.curveOf(id(9), Distance.ofKm(5))).isEmpty();
    }
}
