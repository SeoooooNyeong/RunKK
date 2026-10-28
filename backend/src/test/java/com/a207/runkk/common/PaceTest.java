package com.a207.runkk.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.DisplayName("페이스 값 객체")
class PaceTest {

    @Test
    void of_dividesElapsedByKm() {
        Pace pace = Pace.of(Duration.ofMinutes(55), Distance.ofKm(10));
        assertThat(pace.secondsPerKm()).isEqualTo(330);
    }

    @Test
    void of_rejectsZeroDistance() {
        assertThatThrownBy(() -> Pace.of(Duration.ofMinutes(1), Distance.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsNonPositive() {
        assertThatThrownBy(() -> new Pace(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isSlowerThanBy_usesRatioBoundaryInclusive() {
        Pace target = new Pace(300);
        assertThat(new Pace(330).isSlowerThanBy(target, 0.1)).isTrue();
        assertThat(new Pace(329).isSlowerThanBy(target, 0.1)).isFalse();
        assertThatThrownBy(() -> new Pace(330).isSlowerThanBy(target, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isWithin_includesBounds() {
        Pace fastest = new Pace(180);
        Pace slowest = new Pace(900);
        assertThat(new Pace(180).isWithin(fastest, slowest)).isTrue();
        assertThat(new Pace(900).isWithin(fastest, slowest)).isTrue();
        assertThat(new Pace(179).isWithin(fastest, slowest)).isFalse();
        assertThat(new Pace(901).isWithin(fastest, slowest)).isFalse();
    }

    @Test
    void speedKmh() {
        assertThat(new Pace(300).speedKmh()).isCloseTo(12.0, within(1e-9));
    }

    @Test
    void format_minutesAndSeconds() {
        assertThat(new Pace(330).format(DistanceUnit.KM)).isEqualTo("5'30\"");
        assertThat(new Pace(300).format(DistanceUnit.MI)).isEqualTo("8'03\"");
    }
}
