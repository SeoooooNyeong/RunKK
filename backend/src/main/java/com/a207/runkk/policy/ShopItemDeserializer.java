package com.a207.runkk.policy;

import com.a207.runkk.common.ItemSlot;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * 운영 기준값 SHOP_ITEMS의 아이템 JSON을 읽는다(W8, S15P31A207-299). SettingKey 변환기는 생성자 항목이 빠지거나 null이면 거부하므로,
 * 새 선택 항목(assetKey, unlock)이 없는 옛 저장값을 읽으려고 아이템만 직접 읽는다. 기존 다섯 항목은 예전처럼 필수이고 형식 강제 변환을 하지 않으며, 모르는
 * 항목은 거부한다.
 */
final class ShopItemDeserializer extends StdDeserializer<ShopItem> {

    private static final long serialVersionUID = 1L;

    private static final Set<String> FIELDS =
            Set.of("id", "name", "slot", "price", "onSale", "assetKey", "unlock");

    ShopItemDeserializer() {
        super(ShopItem.class);
    }

    @Override
    public ShopItem deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        JsonNode node = ctx.readTree(p);
        if (!node.isObject()) {
            return ctx.reportInputMismatch(ShopItem.class, "상점 아이템은 객체다");
        }
        for (Iterator<String> names = node.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                return ctx.reportInputMismatch(ShopItem.class, "모르는 항목: %s", name);
            }
        }
        JsonNode id = required(node, "id", ctx);
        JsonNode name = required(node, "name", ctx);
        JsonNode slot = required(node, "slot", ctx);
        JsonNode price = required(node, "price", ctx);
        JsonNode onSale = required(node, "onSale", ctx);
        if (!wholeNumber(id)
                || !id.canConvertToLong()
                || !name.isTextual()
                || !slot.isTextual()
                || !wholeNumber(price)
                || !price.canConvertToInt()
                || !onSale.isBoolean()) {
            return ctx.reportInputMismatch(ShopItem.class, "상점 아이템 항목 형식 오류");
        }
        JsonNode asset = node.get("assetKey");
        if (asset != null && !asset.isNull() && !asset.isTextual()) {
            return ctx.reportInputMismatch(ShopItem.class, "그림 키 형식 오류");
        }
        JsonNode unlock = node.get("unlock");
        try {
            return new ShopItem(
                    id.asLong(),
                    name.asText(),
                    ItemSlot.valueOf(slot.asText()),
                    price.asInt(),
                    onSale.asBoolean(),
                    asset == null || asset.isNull() ? null : asset.asText(),
                    unlock == null || unlock.isNull()
                            ? null
                            : ctx.readTreeAsValue(unlock, ShopUnlock.class));
        } catch (IllegalArgumentException e) {
            return ctx.reportInputMismatch(ShopItem.class, "상점 아이템 값 오류: %s", e.getMessage());
        }
    }

    /**
     * 정수이거나 소수점 아래가 0인 숫자(예전 변환기가 받던 300.0). 문자열·불리언은 받지 않고, 300.5처럼 버림이 생기는 값은 거부한다(예전 변환기는 버렸다).
     */
    private static boolean wholeNumber(JsonNode value) {
        if (value.isIntegralNumber()) {
            return true;
        }
        return value.isFloatingPointNumber()
                && value.decimalValue().stripTrailingZeros().scale() <= 0;
    }

    private static JsonNode required(JsonNode node, String field, DeserializationContext ctx)
            throws IOException {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return ctx.reportInputMismatch(ShopItem.class, "필수 항목 없음: %s", field);
        }
        return value;
    }
}
