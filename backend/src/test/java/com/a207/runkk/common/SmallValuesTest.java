package com.a207.runkk.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** DisplayName, Timestamps.touched 불변, ProgressReport, 예외 형식. */
class SmallValuesTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 20, 9, 0);

    @Test
    void displayName_ofAndWithdrawn() {
        assertThat(DisplayName.of("달리는곰").value()).isEqualTo("달리는곰");
        assertThat(DisplayName.ofNullable("달리는곰")).isEqualTo(DisplayName.of("달리는곰"));
        assertThat(DisplayName.ofNullable(null)).isEqualTo(DisplayName.WITHDRAWN);
        assertThat(DisplayName.WITHDRAWN.value()).isEqualTo("탈퇴한 사용자");
        assertThatThrownBy(() -> DisplayName.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DisplayName.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timestamps_createdAtSetsBoth() {
        Timestamps t = Timestamps.createdAt(T0);
        assertThat(t.createdAt()).isEqualTo(T0);
        assertThat(t.updatedAt()).isEqualTo(T0);
    }

    @Test
    void timestamps_touchedReturnsNewValueAndKeepsOriginal() {
        Timestamps original = Timestamps.createdAt(T0);
        Timestamps touched = original.touched(T0.plusHours(1));
        assertThat(touched).isNotSameAs(original);
        assertThat(touched.createdAt()).isEqualTo(T0);
        assertThat(touched.updatedAt()).isEqualTo(T0.plusHours(1));
        assertThat(original.updatedAt()).isEqualTo(T0);
    }

    @Test
    void timestamps_updatedAtNotBeforeCreatedAt() {
        assertThatThrownBy(() -> new Timestamps(T0, T0.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Timestamps.createdAt(T0).touched(T0.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void progressReport_hasNoLocationField() {
        ProgressReport report =
                new ProgressReport(Distance.ofMeters(1200), Duration.ofSeconds(360), T0);
        assertThat(ProgressReport.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("distance", "elapsed", "sentAt");
        assertThat(report.distance()).isEqualTo(Distance.ofMeters(1200));
    }

    @Test
    void businessRuleException_carriesReasonCodeAndMessage() {
        BusinessRuleException e = new BusinessRuleException(ReasonCode.VALUE_INVALID);
        assertThat(e.reason()).isEqualTo(ReasonCode.VALUE_INVALID);
        assertThat(e.getMessage()).isEqualTo("값 오류");
        assertThat(ReasonCode.TAG_INVALID.message()).isEqualTo("태그 오류");
    }

    @Test
    void permissionDeniedException_hasMessage() {
        assertThat(new PermissionDeniedException("권한 없음").getMessage()).isEqualTo("권한 없음");
    }
}
