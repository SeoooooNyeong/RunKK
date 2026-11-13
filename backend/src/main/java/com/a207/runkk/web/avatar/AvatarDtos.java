package com.a207.runkk.web.avatar;

import com.a207.runkk.avatar.AvatarResult;
import com.a207.runkk.avatar.AvatarView;
import com.a207.runkk.avatar.BackgroundsView;
import com.a207.runkk.avatar.DefaultCharacter;
import com.a207.runkk.avatar.EquipChange;
import com.a207.runkk.avatar.RunnerView;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.ServerZone;
import com.a207.runkk.reward.ShopView;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 아바타·상점 REST 전송 형식(설계 5.7.1, 10/21 API 계약). 앱 api.avatar DTO와 같은 항목 이름을 쓴다. */
final class AvatarDtos {

    private static final String DEFAULT_PREFIX = "default:";

    /** 서비스 시각(LocalDateTime, D-10)을 +09:00 오프셋 일시로 내보낸다. */
    private static final ZoneId SEOUL = ServerZone.ID;

    private AvatarDtos() {}

    /**
     * 아바타 정보(avatar.AvatarView). equipped·rejected 키는 부위 이름. 셀카 요청이면 created·failureReason을 함께 싣는다.
     *
     * @param faceKey 얼굴 키, 없으면 null
     * @param defaultCharacterId 기본 캐릭터 얼굴이면 그 이름
     * @param guide 얼굴이 없을 때 안내문
     * @param bodyShape 체형
     * @param skinTone 피부색
     * @param backgroundKey 고른 랜드마크 배경 키, null이면 기본 배경(v0.11 FR-162, FR-202)
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
            String backgroundKey,
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
                    v.backgroundKey(),
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
                    base.backgroundKey,
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
                                                    e.onSale(),
                                                    e.assetKey(),
                                                    e.locked(),
                                                    e.unlockCondition()))
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
         * @param assetKey 앱 그림 키(영문 소문자·숫자·밑줄), 없으면 null(W8)
         * @param locked 해금 조건을 못 채웠고 보유하지 않아 살 수 없음(W8)
         * @param unlockCondition 해금 조건 문구, 조건이 없으면 null(W8)
         */
        record Item(
                long itemId,
                ItemSlot slot,
                String name,
                int price,
                boolean owned,
                boolean onSale,
                String assetKey,
                boolean locked,
                String unlockCondition) {}
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

    /**
     * 랜드마크 배경 목록(v0.11 GET /avatar/backgrounds, FR-201, FR-202). 순서는 운영 기준값의 개인 랜드마크 순서, 그 뒤에 표에서
     * 빠졌지만 이미 해금한 배경이다.
     *
     * @param selectedKey 고른 배경 키, null이면 기본 배경
     * @param backgrounds 배경
     * @param totalMeters 내 누적 유효 거리(m, W8)
     */
    record BackgroundsResponse(String selectedKey, List<Background> backgrounds, long totalMeters) {

        static BackgroundsResponse of(BackgroundsView view) {
            return new BackgroundsResponse(
                    view.selectedKey(),
                    view.backgrounds().stream()
                            .map(
                                    b ->
                                            new Background(
                                                    b.key(),
                                                    b.name(),
                                                    b.stage(),
                                                    b.distance().meters(),
                                                    b.unlocked(),
                                                    b.unlockedAt() == null
                                                            ? null
                                                            : b.unlockedAt()
                                                                    .atZone(SEOUL)
                                                                    .toOffsetDateTime(),
                                                    b.remaining().meters()))
                            .toList(),
                    view.total().meters());
        }

        /**
         * 배경 하나.
         *
         * @param key 랜드마크 키
         * @param name 랜드마크 이름
         * @param stage DOMESTIC | EAST_ASIA | INTERCONTINENTAL
         * @param distanceMeters 해금 누적 거리(m)
         * @param unlocked 해금됨
         * @param unlockedAt 해금 일시, 잠겼으면 null. 앱 해금 팝업이 기기에 남긴 확인 기록과 비교한다(W8)
         * @param remainingMeters 해금까지 남은 거리(m) = max(0, distanceMeters − totalMeters), 해금했으면 0(W8)
         */
        record Background(
                String key,
                String name,
                String stage,
                long distanceMeters,
                boolean unlocked,
                OffsetDateTime unlockedAt,
                long remainingMeters) {}
    }

    /**
     * 내 러너(W8 GET /avatar/runner, 피그마 H-2, contract-W8.md 2.1·6절).
     *
     * @param bodyShape 체형 4단계(FR-163)
     * @param bodyVariant 화면·W9 렌더러 3단계 체형(0 기본, 1 변화 1, 2 변화 2)
     * @param skinTone 피부톤(시간대, FR-164)
     * @param typicalStartHour 피부톤 근거 대표 시각(시 0~23), 없으면 null
     * @param recentRunCount 오늘 포함 최근 7일 유효 러닝 수
     * @param recentDistanceMeters 같은 7일 유효 거리(m)
     * @param runAmount W9 피부톤 입력 러닝량 0~100
     * @param restDaysBeforeDecline 체형규칙 쉼 일수
     */
    record RunnerResponse(
            String bodyShape,
            int bodyVariant,
            String skinTone,
            Integer typicalStartHour,
            int recentRunCount,
            long recentDistanceMeters,
            int runAmount,
            int restDaysBeforeDecline) {

        static RunnerResponse of(RunnerView v) {
            return new RunnerResponse(
                    v.bodyShape().name(),
                    v.bodyVariant(),
                    v.skinTone().name(),
                    v.typicalStartHour(),
                    v.recentRunCount(),
                    v.recentDistance().meters(),
                    v.runAmount(),
                    v.restDaysBeforeDecline());
        }
    }

    /**
     * 배경 선택(v0.11 PUT /avatar/background, FR-202).
     *
     * @param key 랜드마크 키, null이면 기본 배경
     */
    record BackgroundRequest(String key) {}
}
