package com.a207.runkk.avatar;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.ShopItem;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 여러 회원의 러너 모습(S15P31A207-300). web.record가 개인 랭킹 줄마다 붙인다. 다른 러너에게 아바타는 늘 보이므로(FR-006, D-241) 거르지
 * 않고, 탈퇴 회원 ID는 부르는 쪽이 넘기지 않는다. 회원 수와 관계없이 아바타 묶음 조회 하나, 최근 7일 거리 조회 하나, 상점 목록(운영 기준값) 한 번만 읽는다.
 * 아바타 행이 없는 회원은 새 아바타 기본값으로 주고 행을 만들지 않는다.
 */
@Service
public class RunnerLookQuery {

    private final AvatarRepository avatarRepository;
    private final RunQueryService runQueryService;
    private final OperationPolicy operationPolicy;
    private final AvatarTransactions transactions;
    private final Clock clock;

    RunnerLookQuery(
            AvatarRepository avatarRepository,
            RunQueryService runQueryService,
            OperationPolicy operationPolicy,
            AvatarTransactions transactions,
            Clock clock) {
        this.avatarRepository = avatarRepository;
        this.runQueryService = runQueryService;
        this.operationPolicy = operationPolicy;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * 회원 ID → 모습. 최근 7일 = 오늘 − 6일 ~ 오늘(서버 시간대 날짜, RunnerService와 같은 정의). null ID는 건너뛴다. 넘긴 회원은 모두
     * 맵에 있다.
     */
    public Map<Long, RunnerLook> looksOf(Collection<Long> memberIds) {
        Set<Long> ids = new LinkedHashSet<>();
        memberIds.stream().filter(Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) {
            return Map.of();
        }
        LocalDate today = LocalDate.now(clock);
        DateRange week = new DateRange(today.minusDays(RunnerService.RECENT_DAYS - 1), today);
        return transactions.read(() -> looksOf(ids, week));
    }

    private Map<Long, RunnerLook> looksOf(Set<Long> ids, DateRange week) {
        Map<Long, Avatar> avatars = new HashMap<>();
        avatarRepository.findAllByMemberIds(ids).forEach(a -> avatars.put(a.memberId(), a));
        Map<Long, Distance> distances = runQueryService.distancesByMember(ids, week);
        Map<Long, String> assetKeys = new HashMap<>();
        for (ShopItem item : operationPolicy.shopCatalog().items()) {
            if (item.assetKey() != null) {
                assetKeys.put(item.id(), item.assetKey());
            }
        }
        Map<Long, RunnerLook> looks = new LinkedHashMap<>();
        for (Long id : ids) {
            Avatar avatar = avatars.getOrDefault(id, Avatar.create(id));
            looks.put(
                    id,
                    new RunnerLook(
                            avatar.face().map(Face::renderKey).orElse(null),
                            outfitOf(avatar, assetKeys),
                            avatar.bodyShape().variant(),
                            AppearanceCalculator.runAmount(
                                    distances.getOrDefault(id, Distance.ZERO))));
        }
        return looks;
    }

    private static Map<ItemSlot, String> outfitOf(Avatar avatar, Map<Long, String> assetKeys) {
        Map<ItemSlot, String> outfit = new EnumMap<>(ItemSlot.class);
        avatar.equippedItems()
                .forEach(
                        (slot, itemId) -> {
                            String key = assetKeys.get(itemId);
                            if (key != null) {
                                outfit.put(slot, key);
                            }
                        });
        return outfit;
    }
}
