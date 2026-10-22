package com.a207.runkk.policy;

import com.a207.runkk.common.ItemSlot;

/**
 * 상점 아이템 하나(설계 6.1.9). 판매 중지는 onSale = false로만 한다. 지우지 않는다(D-44).
 *
 * @param id 아이템 ID
 * @param name 이름
 * @param slot 부위
 * @param price 가격, 0보다 큼
 * @param onSale 판매 중 여부
 */
public record ShopItem(long id, String name, ItemSlot slot, int price, boolean onSale) {

    public ShopItem {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("아이템 이름이 비었다");
        }
        if (slot == null) {
            throw new IllegalArgumentException("아이템 부위가 비었다");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("가격은 0보다 커야 한다: " + price);
        }
    }
}
