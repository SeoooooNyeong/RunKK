package com.a207.runkk.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 표시 성별 규칙(D-327·D-336, FR-162·FR-233). */
@DisplayName("표시 성별")
class ShownGenderTest {

    @Test
    @DisplayName("성별 공개인 남·여는 그 값이다")
    void publicGender_isShown() {
        assertThat(ShownGender.of(7, Gender.MALE, ProfileVisibility.PUBLIC)).isEqualTo(Gender.MALE);
        assertThat(ShownGender.of(7, Gender.FEMALE, ProfileVisibility.PUBLIC))
                .isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("비공개·미입력·밝히지 않음은 회원 ID 고정 대체값이고 실제 성별과 무관하다")
    void otherwise_isFixedFallback() {
        Gender fallback = ShownGender.fallbackOf(7);
        assertThat(ShownGender.of(7, Gender.MALE, ProfileVisibility.PRIVATE)).isEqualTo(fallback);
        assertThat(ShownGender.of(7, Gender.FEMALE, ProfileVisibility.PRIVATE)).isEqualTo(fallback);
        assertThat(ShownGender.of(7, null, ProfileVisibility.PUBLIC)).isEqualTo(fallback);
        assertThat(ShownGender.of(7, Gender.UNDISCLOSED, ProfileVisibility.PUBLIC))
                .isEqualTo(fallback);
    }

    @Test
    @DisplayName("대체값은 같은 ID면 늘 같고, 여러 ID에 남·여가 모두 나온다")
    void fallback_isStableAndMixed() {
        assertThat(ShownGender.fallbackOf(42)).isEqualTo(ShownGender.fallbackOf(42));
        Set<Gender> seen = EnumSet.noneOf(Gender.class);
        LongStream.rangeClosed(1, 20).forEach(id -> seen.add(ShownGender.fallbackOf(id)));
        assertThat(seen).containsExactlyInAnyOrder(Gender.MALE, Gender.FEMALE);
    }
}
