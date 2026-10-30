package com.a207.runkk.battle;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.member.Gender;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 대결 상대 캐릭터 성별 규칙(2026-10-31 결정). 무작위는 값을 고정한 생성기로 본다. */
@DisplayName("상대 캐릭터 성별")
class OpponentGendersTest {

    /** nextBoolean = false. */
    private static final RandomGenerator HEADS = () -> 0L;

    /** nextBoolean = true. */
    private static final RandomGenerator TAILS = () -> -1L;

    @Test
    @DisplayName("회원 성별이 남·여면 무작위와 상관없이 그대로 쓴다")
    void memberGender_isKept() {
        assertThat(OpponentGenders.of(Gender.MALE, TAILS)).isEqualTo(Gender.MALE);
        assertThat(OpponentGenders.of(Gender.FEMALE, HEADS)).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("회원 성별이 없거나 밝히지 않았으면 가상 상대처럼 무작위로 정한다")
    void missingGender_isRandom() {
        assertThat(OpponentGenders.of(null, HEADS)).isEqualTo(Gender.MALE);
        assertThat(OpponentGenders.of(null, TAILS)).isEqualTo(Gender.FEMALE);
        assertThat(OpponentGenders.of(Gender.UNDISCLOSED, HEADS)).isEqualTo(Gender.MALE);
        assertThat(OpponentGenders.of(Gender.UNDISCLOSED, TAILS)).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("가상 상대는 남·여 중 하나다")
    void virtual_isRandom() {
        assertThat(OpponentGenders.random(HEADS)).isEqualTo(Gender.MALE);
        assertThat(OpponentGenders.random(TAILS)).isEqualTo(Gender.FEMALE);
    }
}
