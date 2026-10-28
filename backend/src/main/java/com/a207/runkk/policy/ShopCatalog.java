package com.a207.runkk.policy;

import com.a207.runkk.common.ItemSlot;
import java.util.List;
import java.util.Optional;

/**
 * 상점 아이템 목록(설계 6.1.9).
 *
 * @param items 아이템
 */
public record ShopCatalog(List<ShopItem> items) {

    public ShopCatalog {
        items = List.copyOf(items);
    }

    public Optional<ShopItem> find(long id) {
        return items.stream().filter(item -> item.id() == id).findFirst();
    }

    /** 부위가 비면 전체. */
    public List<ShopItem> bySlot(Optional<ItemSlot> slot) {
        return items.stream().filter(item -> slot.map(s -> s == item.slot()).orElse(true)).toList();
    }

    /** avatar가 부위만 읽는다(D-41). */
    public Optional<ItemSlot> slotOf(long id) {
        return find(id).map(ShopItem::slot);
    }

    /** before의 모든 id가 같은 slot으로 남아 있으면 true(D-44). */
    public boolean allowsTransitionFrom(ShopCatalog before) {
        return before.items.stream()
                .allMatch(old -> slotOf(old.id()).map(slot -> slot == old.slot()).orElse(false));
    }
}
