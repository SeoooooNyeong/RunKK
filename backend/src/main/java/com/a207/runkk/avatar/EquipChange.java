package com.a207.runkk.avatar;

import com.a207.runkk.common.ItemSlot;
import java.util.Optional;

/**
 * 아이템 착용 요청 한 항목(srs.md 6.3 6.6 "(아이템ID, 착용 | 해제)"). 부위마다 하나다.
 *
 * @param slot 부위
 * @param itemId 착용할 아이템, null이면 그 부위 해제
 */
public record EquipChange(ItemSlot slot, Long itemId) {

    public EquipChange {
        if (slot == null) {
            throw new IllegalArgumentException("부위가 비었다");
        }
    }

    public static EquipChange wear(ItemSlot slot, long itemId) {
        return new EquipChange(slot, itemId);
    }

    public static EquipChange takeOff(ItemSlot slot) {
        return new EquipChange(slot, null);
    }

    Optional<Long> item() {
        return Optional.ofNullable(itemId);
    }
}
