package com.a207.runkk.web.avatar;

import com.a207.runkk.avatar.AvatarResult;
import com.a207.runkk.avatar.AvatarView;
import com.a207.runkk.avatar.DefaultCharacter;
import com.a207.runkk.avatar.EquipChange;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.reward.ShopView;
import jakarta.validation.constraints.NotNull;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 아바타·상점 REST 전송 형식(설계 5.7.1, 10/21 API 계약). 앱 api.avatar DTO와 같은 항목 이름을 쓴다. */
final class AvatarDtos {

    private static final String DEFAULT_PREFIX = "default:";

    private AvatarDtos() {}

    /**
     * 아바타 정보(avatar.AvatarView). equipped·rejected 키는 부위 이름. 셀카 요청이면 created·failureReason을 함께 싣는다.
     *
     * @param faceKey 얼굴 키, 없으면 null
     * @param defaultCharacterId 기본 캐릭터 얼굴이면 그 이름
     * @param guide 얼굴이 없을 때 안내문
     * @param bodyShape 체형
     * @param skinTone 피부색
     * @param equipped 부위별 착용 아이템
     * @param balance 리워드 잔액
     * @param canCreateSelfieToday 오늘 셀카로 다시 만들 수 있는가
     * @param rejected 착용 거부 부위와 사유 코드
     * @param created 셀카 변환 성공 여부(셀카 요청만)
     * @param failureReason 변환 실패 사유(셀카 요청만)
     * @param failureMessage 변환 실패 문구(셀카 요청만)
     */
    record AvatarResponse(
            String faceKey,
            String defaultCharacterId,
            String guide,
            String bodyShape,
            String skinTone,
            Map<ItemSlot, Long> equipped,
            int balance,
            boolean canCreateSelfieToday,
            Map<ItemSlot, String> rejected,
            Boolean created,
            String failureReason,
            String failureMessage) {

        static AvatarResponse of(AvatarView v) {
            Map<ItemSlot, String> rejected = new EnumMap<>(ItemSlot.class);
            v.rejected().forEach((slot, reason) -> rejected.put(slot, reason.name()));
            String key = v.faceKey();
            return new AvatarResponse(
                    key,
                    key != null && key.startsWith(DEFAULT_PREFIX)
                            ? key.substring(DEFAULT_PREFIX.length())
                            : null,
                    v.guide(),
                    v.bodyShape().name(),
                    v.skinTone().name(),
                    v.equipped(),
                    v.balance(),
                    v.canCreateSelfieToday(),
                    rejected,
                    null,
                    null,
                    null);
        }

        static AvatarResponse of(AvatarResult r) {
            AvatarResponse base = of(r.view());
            return new AvatarResponse(
                    base.faceKey,
                    base.defaultCharacterId,
                    base.guide,
                    base.bodyShape,
                    base.skinTone,
                    base.equipped,
                    base.balance,
                    base.canCreateSelfieToday,
                    base.rejected,
                    r.created(),
                    r.failure() == null ? null : r.failure().name(),
                    r.failure() == null ? null : r.failure().message());
        }
    }

    /**
     * 기본 캐릭터 고르기.
     *
     * @param characterId 4종 중 하나
     */
    record DefaultCharacterRequest(@NotNull DefaultCharacter characterId) {}

    /**
     * 착용 변경. 부위 이름 → 아이템 ID, null이면 그 부위 해제.
     *
     * @param changes 부위별 변경
     */
    record EquipRequest(@NotNull Map<ItemSlot, Long> changes) {

        List<EquipChange> toChanges() {
            return changes.entrySet().stream()
                    .map(e -> new EquipChange(e.getKey(), e.getValue()))
                    .toList();
        }
    }

    /**
     * 상점 목록.
     *
     * @param items 판매 중이거나 보유한 아이템
     * @param balance 리워드 잔액
     */
    record ShopResponse(List<Item> items, int balance) {

        static ShopResponse of(ShopView view) {
            return new ShopResponse(
                    view.items().stream()
                            .map(
                                    e ->
                                            new Item(
                                                    e.itemId(),
                                                    e.slot(),
                                                    e.name(),
                                                    e.price(),
                                                    e.owned(),
                                                    e.onSale()))
                            .toList(),
                    view.balance());
        }

        /**
         * 상점 아이템.
         *
         * @param itemId 아이템 ID
         * @param slot 부위
         * @param name 이름
         * @param price 가격
         * @param owned 보유 여부
         * @param onSale 판매 중
         */
        record Item(
                long itemId,
                ItemSlot slot,
                String name,
                int price,
                boolean owned,
                boolean onSale) {}
    }

    /**
     * 구매.
     *
     * @param itemId 아이템 ID
     */
    record PurchaseRequest(@NotNull Long itemId) {}

    /**
     * 구매 결과.
     *
     * @param itemId 산 아이템
     * @param balance 구매 뒤 잔액
     */
    record PurchaseResponse(long itemId, int balance) {}
}
