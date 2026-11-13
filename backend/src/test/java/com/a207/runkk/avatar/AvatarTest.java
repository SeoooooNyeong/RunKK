package com.a207.runkk.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.SelfieLimit;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 14장 순서 1 엔티티: Avatar 얼굴·착용·체형 상태도(2.4.12)의 전이와 금지 전이. */
@DisplayName("Avatar 상태 전이")
class AvatarTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final SelfieLimit ONCE = new SelfieLimit(1);

    static SdFaceParts parts(String set) {
        Map<FacePart, String> keys = new EnumMap<>(FacePart.class);
        for (FacePart part : FacePart.values()) {
            keys.put(part, set + "/" + part.name());
        }
        return new SdFaceParts(keys);
    }

    @Test
    @DisplayName("새 아바타: 얼굴 없음(기본 캐릭터 안내), 기본 몸(4단계), 착용 없음")
    void create_defaults() {
        Avatar avatar = Avatar.create(7);
        AvatarView view = avatar.view(0);

        assertThat(view.faceKey()).isNull();
        assertThat(view.guide()).isEqualTo(Avatar.CHOOSE_DEFAULT_GUIDE);
        assertThat(view.bodyShape()).isEqualTo(BodyShape.STAGE4);
        assertThat(view.equipped()).isEmpty();
    }

    @Test
    @DisplayName("셀카 얼굴: 하루 1회, 같은 날 두 번째는 SELFIE_LIMIT, 다음 날은 다시 된다")
    void selfie_dailyLimit() {
        Avatar avatar = Avatar.create(7);
        assertThat(avatar.canCreateSelfie(TODAY, ONCE)).isTrue();

        avatar.applySelfieFace(parts("face/7/a"), TODAY, ONCE);

        assertThat(avatar.creationCount()).isEqualTo(1);
        assertThat(avatar.canCreateSelfie(TODAY, ONCE)).isFalse();
        assertThat(avatar.view(0).faceKey()).isEqualTo("selfie:face/7/a");
        assertThatThrownBy(() -> avatar.applySelfieFace(parts("face/7/b"), TODAY, ONCE))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.SELFIE_LIMIT);
        assertThat(avatar.canCreateSelfie(TODAY, new SelfieLimit(2))).isTrue();
        assertThat(avatar.canCreateSelfie(TODAY.plusDays(1), ONCE)).isTrue();
    }

    @Test
    @DisplayName("기본 캐릭터는 셀카 얼굴이 있어도 바꿀 수 있고 생성 횟수를 바꾸지 않는다")
    void chooseDefault_overSelfie() {
        Avatar avatar = Avatar.create(7);
        avatar.applySelfieFace(parts("face/7/a"), TODAY, ONCE);

        avatar.chooseDefault(DefaultCharacter.BEAR);

        assertThat(avatar.view(0).faceKey()).isEqualTo("default:BEAR");
        assertThat(avatar.creationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("착용은 같은 부위를 교체하고 해제는 그 부위를 비운다")
    void equip_replacesSameSlot() {
        Avatar avatar = Avatar.create(7);
        avatar.equip(1, ItemSlot.HAT);
        avatar.equip(2, ItemSlot.HAT);
        avatar.equip(3, ItemSlot.TOP);

        assertThat(avatar.view(5).equipped())
                .containsExactlyInAnyOrderEntriesOf(Map.of(ItemSlot.HAT, 2L, ItemSlot.TOP, 3L));
        avatar.unequip(ItemSlot.HAT);
        avatar.unequip(ItemSlot.SHOES);
        assertThat(avatar.view(5).equipped()).containsOnlyKeys(ItemSlot.TOP);
        assertThat(avatar.view(5).balance()).isEqualTo(5);
    }

    @Test
    @DisplayName("외형 갱신은 체형이 바뀔 때만 true(알림 대상), 피부색만 바뀌면 false")
    void updateAppearance() {
        Avatar avatar = Avatar.create(7);
        assertThat(avatar.updateAppearance(BodyShape.STAGE3, SkinTone.DAWN)).isTrue();
        assertThat(avatar.updateAppearance(BodyShape.STAGE3, SkinTone.NIGHT)).isFalse();
        assertThat(avatar.bodyShape()).isEqualTo(BodyShape.STAGE3);
        assertThat(avatar.skinTone()).isEqualTo(SkinTone.NIGHT);
    }

    @ParameterizedTest
    @CsvSource({"STAGE4, STAGE3", "STAGE3, STAGE2", "STAGE2, STAGE1", "STAGE1, STAGE1"})
    @DisplayName("체형 한 단계 내림, 1단계 아래로는 내려가지 않는다")
    void lowerOneStep(BodyShape from, BodyShape to) {
        assertThat(from.lowerOneStep()).isEqualTo(to);
    }

    @Test
    @DisplayName("policy 번호 → 피부색(SkinToneRule 0~4)")
    void fromPolicyNumbers() {
        assertThat(SkinTone.ofBand(0)).isEqualTo(SkinTone.DAWN);
        assertThat(SkinTone.ofBand(4)).isEqualTo(SkinTone.NIGHT);
        assertThatThrownBy(() -> SkinTone.ofBand(5)).isInstanceOf(IllegalArgumentException.class);
    }
}
