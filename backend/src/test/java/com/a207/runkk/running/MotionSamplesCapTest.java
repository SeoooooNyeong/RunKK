package com.a207.runkk.running;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MR !91 확인 리뷰 CR2-B1: 상한을 넘는 움직임 표본은 같은 일시를 합친 뒤 시간 칸으로 묶어 속도·케이던스를 따로 평균한다. */
@DisplayName("움직임 표본 상한 솎기")
class MotionSamplesCapTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 8, 7, 0);
    private static final int MAX = RunUpload.MAX_MOTIONS;

    @Test
    @DisplayName("속도와 케이던스가 번갈아 와도 솎은 뒤 두 값이 모두 남는다")
    void alternatingSpeedAndCadence_bothSurvive() {
        List<MotionSample> samples = new ArrayList<>();
        for (int s = 0; s < 50_000; s++) {
            samples.add(new MotionSample(T0.plusSeconds(s), 3.0, null));
            samples.add(new MotionSample(T0.plusSeconds(s).plusNanos(500_000_000), null, 170.0));
        }

        List<MotionSample> capped = MotionSamples.capped(samples, MAX);

        assertThat(capped).hasSizeLessThanOrEqualTo(MAX);
        // 예전 방식(k번째마다 하나)은 케이던스가 0개 남았다. 칸으로 묶으면 두 값이 거의 모든 초에 남는다
        assertThat(capped.stream().filter(m -> m.speedMps() != null).count()).isGreaterThan(45_000);
        assertThat(capped.stream().filter(m -> m.cadenceSpm() != null).count())
                .isGreaterThan(45_000);
        assertThat(capped)
                .allSatisfy(
                        m -> {
                            if (m.speedMps() != null) {
                                assertThat(m.speedMps()).isEqualTo(3.0);
                            }
                            if (m.cadenceSpm() != null) {
                                assertThat(m.cadenceSpm()).isEqualTo(170.0);
                            }
                        });
    }

    @Test
    @DisplayName("86,401개는 상한 안으로 줄되 절반으로 줄지 않는다")
    void justOverLimit_isNotHalved() {
        List<MotionSample> samples = new ArrayList<>();
        for (int s = 0; s <= MAX; s++) {
            samples.add(new MotionSample(T0.plusSeconds(s), 3.0 + (s % 2), null));
        }

        List<MotionSample> capped = MotionSamples.capped(samples, MAX);

        assertThat(capped).hasSizeLessThanOrEqualTo(MAX).hasSizeGreaterThan(MAX * 9 / 10);
        assertThat(capped.get(0).measuredAt()).isEqualTo(T0);
        assertThat(capped)
                .extracting(MotionSample::measuredAt)
                .isSortedAccordingTo(LocalDateTime::compareTo);
    }

    @Test
    @DisplayName("같은 일시 표본이 겹쳐 넘친 것은 합치기만으로 상한 안에 든다")
    void duplicatesOnly_areMergedNotThinned() {
        List<MotionSample> samples = new ArrayList<>();
        for (int s = 0; s < 50_000; s++) {
            samples.add(new MotionSample(T0.plusSeconds(s), 3.0, null));
            samples.add(new MotionSample(T0.plusSeconds(s), null, 172.0));
        }

        List<MotionSample> capped = MotionSamples.capped(samples, MAX);

        assertThat(capped).hasSize(50_000);
        assertThat(capped.get(10)).isEqualTo(new MotionSample(T0.plusSeconds(10), 3.0, 172.0));
    }

    @Test
    @DisplayName("상한 안이면 그대로 둔다")
    void withinLimit_unchanged() {
        List<MotionSample> samples =
                List.of(
                        new MotionSample(T0.plusSeconds(2), 3.0, null),
                        new MotionSample(T0.plusSeconds(1), null, 170.0));

        assertThat(MotionSamples.capped(samples, MAX)).isEqualTo(samples);
    }
}
