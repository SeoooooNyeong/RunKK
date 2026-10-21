package com.a207.runkk.reward;

import com.a207.runkk.policy.ShopItem;
import java.util.List;

/**
 * 상점 목록(자료사전 상점목록, srs.md 6.3 6.5).
 *
 * @param items 판매 중이거나 이미 보유한 아이템과 보유 여부
 * @param balance 리워드 잔액
 */
public record ShopView(List<Entry> items, int balance) {

    public ShopView {
        items = List.copyOf(items);
    }

    /**
     * 상점 아이템 한 줄.
     *
     * @param item 아이템
     * @param owned 보유 여부
     */
    public record Entry(ShopItem item, boolean owned) {}
}
