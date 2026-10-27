package com.a207.runkk.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.Landmark;
import com.a207.runkk.policy.LandmarkStage;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 14장 순서 1 엔티티: Avatar 배경 해금·선택(6.1.8, D-107, FR-201, FR-202). 02-01 TC-6160·TC-6161의 엔티티 부분. */
@DisplayName("Avatar 랜드마크 배경")
class AvatarBackgroundTest {

    private static final LocalDateTime N = LocalDateTime.of(2026, 10, 22, 9, 0);
    static final Landmark INCHEON =
            new Landmark("INCHEON", LandmarkStage.DOMESTIC, "인천", Distance.ofKm(60), true);
    static final Landmark KAESONG =
            new Landmark("KAESONG", LandmarkStage.DOMESTIC, "개성", Distance.ofKm(120), true);

    @Test
    @DisplayName("TC-6160 ① 같은 랜드마크를 두 번 넘겨도 하나만 더하고 새로 해금한 것만 돌려준다")
    void unlock_addsOnlyNewKeys() {
        Avatar avatar = Avatar.create(3);

        List<UnlockedBackground> added = avatar.unlock(List.of(INCHEON, INCHEON), N);

        assertThat(added).extracting(UnlockedBackground::landmarkKey).containsExactly("INCHEON");
        UnlockedBackground u = added.get(0);
        assertThat(u.landmarkName()).isEqualTo("인천");
        assertThat(u.stage()).isEqualTo(LandmarkStage.DOMESTIC);
        assertThat(u.distance()).isEqualTo(Distance.ofKm(60));
        assertThat(u.unlockedAt()).isEqualTo(N);
        assertThat(avatar.unlock(List.of(INCHEON, KAESONG), N.plusHours(1)))
                .extracting(UnlockedBackground::landmarkKey)
                .containsExactly("KAESONG");
        assertThat(avatar.unlockedBackgrounds()).hasSize(2);
    }

    @Test
    @DisplayName("TC-6160 ② 기준값에서 이름이 바뀌어도 이미 해금한 키는 새로 더하지 않고 스냅숏 이름이 남는다")
    void unlock_keepsSnapshot() {
        Avatar avatar = Avatar.create(3);
        avatar.unlock(List.of(INCHEON), N);
        Landmark renamed =
                new Landmark("INCHEON", LandmarkStage.DOMESTIC, "인천공항", Distance.ofKm(70), true);

        assertThat(avatar.unlock(List.of(renamed), N.plusDays(1))).isEmpty();
        assertThat(avatar.unlockedBackgrounds().get(0).landmarkName()).isEqualTo("인천");
        assertThat(avatar.unlockedBackgrounds().get(0).distance()).isEqualTo(Distance.ofKm(60));
    }

    @Test
    @DisplayName("TC-6161 해금한 키만 고를 수 있고 비어 있으면 기본 배경, 잠긴 키는 BACKGROUND_LOCKED이고 선택은 그대로다")
    void selectBackground() {
        Avatar avatar = Avatar.create(3);
        avatar.unlock(List.of(INCHEON, KAESONG), N);

        avatar.selectBackground("KAESONG");
        assertThat(avatar.selectedBackgroundKey()).contains("KAESONG");
        assertThat(avatar.view(0).backgroundKey()).isEqualTo("KAESONG");

        for (String locked : List.of("BUSAN", "NO_SUCH")) {
            assertThatThrownBy(() -> avatar.selectBackground(locked))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((BusinessRuleException) e).reason())
                    .isEqualTo(ReasonCode.BACKGROUND_LOCKED);
            assertThat(avatar.selectedBackgroundKey()).contains("KAESONG");
        }

        avatar.useDefaultBackground();
        assertThat(avatar.selectedBackgroundKey()).isEmpty();
        assertThat(avatar.view(0).backgroundKey()).isNull();
    }
}
