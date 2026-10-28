package com.a207.runkk.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.DisplayName("거리 값 객체")
class DistanceTest {

    @Test
    void ofMeters_rejectsNegative() {
        assertThatThrownBy(() -> Distance.ofMeters(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Distance.ofMeters(0)).isEqualTo(Distance.ZERO);
    }

    @Test
    void ofKm_convertsAndRejectsNegative() {
        assertThat(Distance.ofKm(1.5).meters()).isEqualTo(1500);
        assertThatThrownBy(() -> Distance.ofKm(-0.1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void plusAndMinus_neverGoNegative() {
        Distance five = Distance.ofMeters(5000);
        Distance three = Distance.ofMeters(3000);
        assertThat(five.plus(three).meters()).isEqualTo(8000);
        assertThat(five.minus(three).meters()).isEqualTo(2000);
        assertThat(three.minus(five)).isEqualTo(Distance.ZERO);
    }

    @Test
    void isAtLeast_includesEqual() {
        assertThat(Distance.ofMeters(3000).isAtLeast(Distance.ofMeters(3000))).isTrue();
        assertThat(Distance.ofMeters(2999).isAtLeast(Distance.ofMeters(3000))).isFalse();
    }

    @Test
    void km_roundsToTwoDecimals() {
        assertThat(Distance.ofMeters(5126).km()).isEqualTo(5.13);
        assertThat(Distance.ofMeters(5124).km()).isEqualTo(5.12);
    }

    @Test
    void format_convertsOnlyForDisplay() {
        Distance tenK = Distance.ofMeters(10_000);
        assertThat(tenK.format(DistanceUnit.KM)).isEqualTo("10.00 km");
        assertThat(tenK.format(DistanceUnit.MI)).isEqualTo("6.21 mi");
        assertThat(tenK.meters()).isEqualTo(10_000);
    }

    @Test
    void compareTo_ordersByMeters() {
        assertThat(Distance.ofMeters(1).compareTo(Distance.ofMeters(2))).isNegative();
        assertThat(Distance.ofMeters(2).compareTo(Distance.ofMeters(2))).isZero();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("L5: exactKm은 반올림하지 않은 km 환산이다")
    void exactKm_isUnrounded() {
        assertThat(Distance.ofMeters(1_234).exactKm()).isEqualTo(1.234);
        assertThat(Distance.ofMeters(1_235).km()).isEqualTo(1.24);
    }
}
