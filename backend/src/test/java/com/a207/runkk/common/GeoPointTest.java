package com.a207.runkk.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GeoPointTest {

    @Test
    void rejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() -> new GeoPoint(90.1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(0, -180.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void distanceTo_usesHaversine() {
        GeoPoint seoulCityHall = new GeoPoint(37.5665, 126.9780);
        GeoPoint gangnam = new GeoPoint(37.4979, 127.0276);
        long meters = seoulCityHall.distanceTo(gangnam).meters();
        assertThat(meters).isBetween(8_700L, 8_900L);
        assertThat(seoulCityHall.distanceTo(seoulCityHall)).isEqualTo(Distance.ZERO);
    }

    @Test
    void distanceTo_oneDegreeOfLatitude() {
        long meters = new GeoPoint(0, 0).distanceTo(new GeoPoint(1, 0)).meters();
        assertThat(meters).isEqualTo(111_195);
    }

    @Test
    void coarsen_roundsDecimals() {
        GeoPoint p = new GeoPoint(37.56659, 126.97801);
        assertThat(p.coarsen(2)).isEqualTo(new GeoPoint(37.57, 126.98));
        assertThat(p.coarsen(0)).isEqualTo(new GeoPoint(38, 127));
        assertThatThrownBy(() -> p.coarsen(7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.coarsen(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
