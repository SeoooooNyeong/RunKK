package com.a207.runkk.running;

import static com.a207.runkk.running.PauseJudgementTest.pause;
import static com.a207.runkk.running.PauseJudgementTest.t;
import static com.a207.runkk.running.RunFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 시험 2.1.10.3(TC-6026~TC-6033): RunUpload 생성자의 정지 구간 검사(부록 F F.2.2 POST /runs 규칙, D-163 맞닿음 거부).
 * 위반은 IllegalArgumentException(→ 400).
 */
@DisplayName("RunUpload 정지 구간 검사(FR-194)")
class RunUploadPauseTest {

    private static final LocalDateTime START = t(7, 0, 0);
    private static final LocalDateTime END = t(7, 40, 0);

    private static RunUpload upload(RunMode mode, List<PauseInterval> pauses) {
        CardKind card =
                switch (mode) {
                    case FREE -> CardKind.FREE_RUN;
                    case BATTLE -> CardKind.BATTLE_RUN;
                    case CREW_RUN -> CardKind.CREW_RUN;
                };
        return new RunUpload(
                id(1),
                START,
                END,
                Distance.ofKm(5),
                Duration.ofMinutes(30),
                mode,
                card,
                mode == RunMode.BATTLE ? 7L : null,
                mode == RunMode.CREW_RUN ? 9L : null,
                0,
                List.of(),
                List.of(),
                List.of(),
                pauses);
    }

    private static RunUpload free(List<PauseInterval> pauses) {
        return upload(RunMode.FREE, pauses);
    }

    private static List<PauseInterval> oneSecondPauses(int n) {
        List<PauseInterval> pauses = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LocalDateTime from = START.plusSeconds(2L * i + 1);
            pauses.add(pause(from, from.plusSeconds(1)));
        }
        return pauses;
    }

    @Test
    @DisplayName("TC-6026 U1·U2: null·빈 목록은 모든 모드에서 정지 없음")
    void tc6026_nullOrEmpty() {
        assertThat(free(null).pauses()).isEmpty();
        assertThat(upload(RunMode.BATTLE, List.of()).pauses()).isEmpty();
    }

    @Test
    @DisplayName(
            "TC-6027 U3: 대결에 정지 구간이 있으면 IllegalArgumentException, 크루런은 받는다(FR-300, MR !91 BR-1)")
    void tc6027_battleRejectsPauses_crewRunAccepts() {
        List<PauseInterval> one = List.of(pause(t(7, 10, 0), t(7, 20, 0)));
        assertThatThrownBy(() -> upload(RunMode.BATTLE, one))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(upload(RunMode.CREW_RUN, one).pauses()).isEqualTo(one);
        assertThat(RunRecord.receive(1L, upload(RunMode.CREW_RUN, one)).pausedTotal())
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("TC-6028 U4: 99·100개 정상, 101개 거부(크루런도 같다)")
    void tc6028_maxCount() {
        assertThat(free(oneSecondPauses(99)).pauses()).hasSize(99);
        assertThat(free(oneSecondPauses(RunUpload.MAX_PAUSES)).pauses()).hasSize(100);
        assertThatThrownBy(() -> free(oneSecondPauses(101)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> upload(RunMode.CREW_RUN, oneSecondPauses(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-6029 U6: startedAt ≤ from 경곗값")
    void tc6029_startBoundary() {
        LocalDateTime end = t(7, 5, 0);
        assertThatThrownBy(() -> free(List.of(pause(START.minusNanos(1_000_000), end))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> free(List.of(pause(START, end)))).doesNotThrowAnyException();
        assertThatCode(() -> free(List.of(pause(START.plusNanos(1_000_000), end))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TC-6030 U7: to ≤ endedAt 경곗값(자동 종료면 마지막 구간 끝 = 종료 시각)")
    void tc6030_endBoundary() {
        LocalDateTime from = t(7, 30, 0);
        assertThatThrownBy(() -> free(List.of(pause(from, END.plusNanos(1_000_000)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> free(List.of(pause(from, END)))).doesNotThrowAnyException();
        assertThatCode(() -> free(List.of(pause(from, END.minusNanos(1_000_000)))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TC-6031 U8: 구간 1개 정상, receive 뒤 pausedTotal 10:00")
    void tc6031_receive() {
        RunRecord r = RunRecord.receive(1L, free(List.of(pause(t(7, 10, 0), t(7, 20, 0)))));
        assertThat(r.pausedTotal()).isEqualTo(Duration.ofMillis(600_000));
    }

    @Test
    @DisplayName("TC-6032 U9: 겹침·순서 뒤바뀜은 거부")
    void tc6032_overlapOrReversed() {
        assertThatThrownBy(
                        () ->
                                free(
                                        List.of(
                                                pause(t(7, 10, 0), t(7, 20, 0)),
                                                pause(t(7, 15, 0), t(7, 25, 0)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                free(
                                        List.of(
                                                pause(t(7, 20, 0), t(7, 25, 0)),
                                                pause(t(7, 10, 0), t(7, 15, 0)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-6033 U9·U10: 떨어진 두 구간은 정상(정지 15:00), 맞닿은 구간은 거부(D-163)")
    void tc6033_touching() {
        RunUpload gap =
                free(List.of(pause(t(7, 10, 0), t(7, 20, 0)), pause(t(7, 25, 0), t(7, 30, 0))));
        assertThat(RunRecord.receive(1L, gap).pausedTotal()).isEqualTo(Duration.ofMinutes(15));
        assertThatThrownBy(
                        () ->
                                free(
                                        List.of(
                                                pause(t(7, 10, 0), t(7, 20, 0)),
                                                pause(t(7, 20, 0), t(7, 30, 0)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("심박 동의가 없어 심박을 뺀 사본도 정지 구간을 그대로 둔다")
    void withoutHeartRates_keepsPauses() {
        List<PauseInterval> one = List.of(pause(t(7, 10, 0), t(7, 20, 0)));
        assertThat(free(one).withoutHeartRates().pauses()).isEqualTo(one);
    }
}
