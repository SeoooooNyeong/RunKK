package com.a207.runkk.policy;

import com.a207.runkk.common.ItemSlot;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 상점 아이템 하나(설계 6.1.9). 판매 중지는 onSale = false로만 한다. 지우지 않는다(D-44). W8(S15P31A207-299)에서 그림 키와 해금 조건을
 * 선택 항목으로 더했다. 운영 기준값 JSON은 옛 형식(두 항목 없음)도 읽는다(ShopItemDeserializer).
 *
 * @param id 아이템 ID
 * @param name 이름
 * @param slot 부위
 * @param price 가격, 0보다 큼
 * @param onSale 판매 중 여부
 * @param assetKey 앱 그림 키(영문 소문자·숫자·밑줄 1~40자), 없으면 null
 * @param unlock 해금 조건, 없으면 null
 */
@JsonDeserialize(using = ShopItemDeserializer.class)
public record ShopItem(
        long id,
        String name,
        ItemSlot slot,
        int price,
        boolean onSale,
        String assetKey,
        ShopUnlock unlock) {

    private static final Pattern ASSET_KEY = Pattern.compile("[a-z0-9_]{1,40}");

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
        if (assetKey != null && !ASSET_KEY.matcher(assetKey).matches()) {
            throw new IllegalArgumentException("그림 키 형식 오류");
        }
    }

    /** 그림 키·해금 조건이 없는 아이템. */
    public ShopItem(long id, String name, ItemSlot slot, int price, boolean onSale) {
        this(id, name, slot, price, onSale, null, null);
    }

    /** 해금 조건. 없으면 비어 있다. */
    public Optional<ShopUnlock> unlockCondition() {
        return Optional.ofNullable(unlock);
    }

    /** 가격만 바꾼 아이템(ITEM_PRICES 덮어쓰기). */
    public ShopItem withPrice(int newPrice) {
        return new ShopItem(id, name, slot, newPrice, onSale, assetKey, unlock);
    }
}
