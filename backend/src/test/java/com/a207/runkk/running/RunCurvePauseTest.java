package com.a207.runkk.running;

import static com.a207.runkk.running.PauseJudgementTest.concat;
import static com.a207.runkk.running.PauseJudgementTest.free;
import static com.a207.runkk.running.PauseJudgementTest.leg;
import static com.a207.runkk.running.PauseJudgementTest.pause;
import static com.a207.runkk.running.PauseJudgementTest.t;
import static com.a207.runkk.running.RunFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 총괄 결정(설계 쟁점 7-4): 정지가 있는 자유런을 고스트·나와의 싸움 상대 트랙(RunQueryService.curve)으로 재생할 때는 운동 시간 축으로 바꾼다. 정지
 * 구간을 잘라 내고 이후 포인트의 경과 시간을 정지 합만큼 당겨 상대 진행도가 정지 없는 러닝처럼 보인다. 정지 없는 기록은 그대로다.
 */
@DisplayName("상대 트랙 curve의 운동 시간 축(총괄 결정 7-4)")
class RunCurvePauseTest {

    private static final double V = 5_000 / 1_800.0;

    private final InMemoryRunningRepositories.Records records =
            new InMemoryRunningRepositories.Records();
    private final RunQueryService query =
            new RunQueryService(records, new InMemoryRunningRepositories.Books(), null);

    private void store(RunUpload upload) {
        RunRecord r = RunRecord.receive(1L, upload);
        r.judgeValidity(ValidityRule.STANDARD);
        records.save(r);
    }

    private static long meters(RunCurve curve, int second) {
        CurvePoint p = curve.samples().get(second);
        assertThat(p.elapsed()).isEqualTo(Duration.ofSeconds(second));
        return p.distance().meters();
    }

    @Test
    @DisplayName("정지 1개: [07:10, 07:20]을 잘라 600초 뒤로도 거리가 계속 늘고 5km는 운동 30:00에 끝난다")
    void onePause() {
        store(
                free(
                        id(1),
                        t(7, 0, 0),
                        t(7, 40, 0),
                        5_000,
                        Duration.ofMinutes(30),
                        concat(
                                leg(t(7, 0, 0), t(7, 10, 0), 0, V),
                                leg(t(7, 20, 0), t(7, 40, 0), 600 * V, V)),
                        List.of(pause(t(7, 10, 0), t(7, 20, 0)))));

        RunCurve curve = query.curve(id(1), Distance.ofKm(5));

        assertThat(curve.samples()).hasSize(1_801);
        assertThat((double) meters(curve, 600)).isCloseTo(600 * V, within(1.0));
        assertThat((double) meters(curve, 601)).isCloseTo(601 * V, within(1.0));
        assertThat((double) meters(curve, 900)).isCloseTo(2_500, within(1.0));
        CurvePoint last = curve.samples().get(1_800);
        assertThat(last.elapsed().toMillis()).isCloseTo(1_800_000L, within(5L));
        assertThat(last.distance()).isEqualTo(Distance.ofKm(5));
    }

    @Test
    @DisplayName(
            "정지 2개: [07:10, 07:15]·[07:25, 07:30]을 잘라 운동 시간 600·1,200·1,500초의 거리가 정지 없는 러닝과 같다")
    void twoPauses() {
        store(
                free(
                        id(2),
                        t(7, 0, 0),
                        t(7, 40, 0),
                        5_000,
                        Duration.ofMinutes(30),
                        concat(
                                concat(
                                        leg(t(7, 0, 0), t(7, 10, 0), 0, V),
                                        leg(t(7, 15, 0), t(7, 25, 0), 600 * V, V)),
                                leg(t(7, 30, 0), t(7, 40, 0), 1_200 * V, V)),
                        List.of(pause(t(7, 10, 0), t(7, 15, 0)), pause(t(7, 25, 0), t(7, 30, 0)))));

        RunCurve curve = query.curve(id(2), Distance.ofKm(5));

        assertThat(curve.samples()).hasSize(1_801);
        for (int second : new int[] {300, 600, 900, 1_200, 1_500}) {
            assertThat((double) meters(curve, second)).isCloseTo(second * V, within(1.0));
        }
        assertThat(curve.samples().get(1_800).distance()).isEqualTo(Distance.ofKm(5));

        RunCurve firstKm = query.curve(id(2), Distance.ofKm(1));
        assertThat(firstKm.samples().get(firstKm.samples().size() - 1).elapsed().toMillis())
                .isCloseTo(360_000L, within(5L));
    }

    @Test
    @DisplayName("정지 없는 기록은 벽시계 축 그대로(경과 = 측정 시각 − 시작)")
    void noPauseUnchanged() {
        store(RunFixtures.upload(id(3), 5_000, 1_500));
        RunTrack track = records.findById(id(3)).orElseThrow().track();

        RunCurve curve = query.curve(id(3), Distance.ofKm(5));

        assertThat(curve.samples()).hasSize(1_501);
        assertThat(curve.samples())
                .isEqualTo(
                        track.curve(
                                Duration.ofSeconds(1),
                                track.timeToReach(Distance.ofKm(5)).orElseThrow()));
        assertThat((double) meters(curve, 750)).isCloseTo(2_500, within(1.0));
    }
}
