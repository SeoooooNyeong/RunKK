package com.a207.runkk.avatar;

import com.a207.runkk.common.ItemSlot;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 다른 러너에게 보이는 회원 모습(S15P31A207-300). 랭킹 줄마다 붙여 앱이 그 회원의 얼굴·의상·체형·그을림으로 그린다. web.record
 * RankingResponse.Entry.look으로 바꾼다.
 *
 * @param faceKey 얼굴 키(Face.renderKey, AvatarQuery.faceKey와 같은 값), 얼굴을 고르지 않았으면 null
 * @param outfit 착용 부위 → 그 아이템 그림 키(ShopItem.assetKey). 그림 키가 없거나 상점 목록에 없는 아이템 부위는 빠진다. 부위
 *     순서(ItemSlot)로 둔다
 * @param bodyVariant 3단계 체형(0 기본 ~ 2 가장 통통, BodyShape.variant, RunnerView.bodyVariant와 같은 규칙)
 * @param runAmount 러닝량 0~100(AppearanceCalculator.runAmount, 최근 7일 유효 거리)
 */
public record RunnerLook(
        String faceKey, Map<ItemSlot, String> outfit, int bodyVariant, int runAmount) {

    private static final int MAX_VARIANT = 2;
    private static final int MAX_RUN_AMOUNT = 100;

    public RunnerLook {
        if (bodyVariant < 0 || bodyVariant > MAX_VARIANT) {
            throw new IllegalArgumentException("체형 단계는 0~2: " + bodyVariant);
        }
        if (runAmount < 0 || runAmount > MAX_RUN_AMOUNT) {
            throw new IllegalArgumentException("러닝량은 0~100: " + runAmount);
        }
        Map<ItemSlot, String> copy = new EnumMap<>(ItemSlot.class);
        if (outfit != null) {
            copy.putAll(outfit);
        }
        outfit = Collections.unmodifiableMap(copy);
    }
}
