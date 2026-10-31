package com.a207.runkk.battle;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.member.Gender;
import com.a207.runkk.member.ShownGender;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 대결 상대 캐릭터 성별 규칙(2026-10-31 결정, D-336). 무작위는 값을 고정한 생성기로 본다. */
@DisplayName("상대 캐릭터 성별")
class OpponentGendersTest {

    /** nextBoolean = false. */
    private static final RandomGenerator HEADS = () -> 0L;

    /** nextBoolean = true. */
    private static final RandomGenerator TAILS = () -> -1L;

    @Test
    @DisplayName("회원 상대는 표시 성별 맵의 값을 쓴다")
    void memberGender_isShown() {
        assertThat(OpponentGenders.shownOf(Map.of(7L, Gender.MALE), 7L)).isEqualTo(Gender.MALE);
        assertThat(OpponentGenders.shownOf(Map.of(7L, Gender.FEMALE), 7L)).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("맵에 없거나 남·여가 아니면 회원 ID 고정 대체값이고, 같은 회원은 대결마다 같다")
    void missingGender_isFixedFallback() {
        assertThat(OpponentGenders.shownOf(Map.of(), 7L)).isEqualTo(ShownGender.fallbackOf(7L));
        assertThat(OpponentGenders.shownOf(Map.of(7L, Gender.UNDISCLOSED), 7L))
                .isEqualTo(ShownGender.fallbackOf(7L));
        assertThat(OpponentGenders.shownOf(Map.of(), 7L))
                .isEqualTo(OpponentGenders.shownOf(Map.of(), 7L));
    }

    @Test
    @DisplayName("가상 상대는 남·여 중 하나다")
    void virtual_isRandom() {
        assertThat(OpponentGenders.random(HEADS)).isEqualTo(Gender.MALE);
        assertThat(OpponentGenders.random(TAILS)).isEqualTo(Gender.FEMALE);
    }
}
