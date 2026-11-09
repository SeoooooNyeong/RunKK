package com.a207.runkk.reward;

import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.policy.ShopItem;
import com.a207.runkk.policy.ShopUnlock;
import java.util.List;

/**
 * 상점 목록(자료사전 상점목록: 아이템ID + 아이템명 + 아이템부위 + 가격 + 보유여부, srs.md 6.3 6.5). web.avatar가 policy를 import하지
 * 않도록(12.2 의존 목록) 상점 아이템을 항목으로 풀어 담는다.
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
     * @param itemId 아이템 ID
     * @param name 아이템명
     * @param slot 부위
     * @param price 가격
     * @param onSale 판매 중
     * @param owned 보유 여부
     * @param assetKey 앱 그림 키, 없으면 null(W8)
     * @param locked 해금 조건을 못 채웠고 보유하지 않음(W8)
     * @param unlockCondition 해금 조건 문구, 조건이 없으면 null(W8)
     */
    public record Entry(
            long itemId,
            String name,
            ItemSlot slot,
            int price,
            boolean onSale,
            boolean owned,
            String assetKey,
            boolean locked,
            String unlockCondition) {

        static Entry of(ShopItem item, boolean owned, boolean locked) {
            return new Entry(
                    item.id(),
                    item.name(),
                    item.slot(),
                    item.price(),
                    item.onSale(),
                    owned,
                    item.assetKey(),
                    locked,
                    item.unlockCondition().map(ShopUnlock::text).orElse(null));
        }
    }
}
