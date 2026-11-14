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
 * 아바타 행이 없는 회원은 새 아바타 기본값으로 주고 행을 만들지 않는다. 랭킹 줄은 {@link #snapshotLooksOf}로 0시 스냅숏 단위에 고정한다(PM
 * 2026-11-14, RunnerLookSnapshotStore).
 */
@Service
public class RunnerLookQuery {

    private final AvatarRepository avatarRepository;
    private final RunQueryService runQueryService;
    private final OperationPolicy operationPolicy;
    private final AvatarTransactions transactions;
    private final Clock clock;
    private final RunnerLookSnapshotStore snapshotStore;

    RunnerLookQuery(
            AvatarRepository avatarRepository,
            RunQueryService runQueryService,
            OperationPolicy operationPolicy,
            AvatarTransactions transactions,
            Clock clock,
            RunnerLookSnapshotStore snapshotStore) {
        this.avatarRepository = avatarRepository;
        this.runQueryService = runQueryService;
        this.operationPolicy = operationPolicy;
        this.transactions = transactions;
        this.clock = clock;
        this.snapshotStore = snapshotStore;
    }

    /**
     * 스냅숏 하나에 고정한 회원 모습(PM 2026-11-14). 저장소에 있는 회원은 그 값을 그대로 주고, 없는 회원만 {@link #looksOf}로 읽어 저장소에
     * 더한다. 그래서 같은 스냅숏을 보는 동안 모든 사용자가 같은 모습을 읽고, 0시 스냅숏 뒤 첫 조회만 DB를 읽는다(상위 밖 본인 줄은 그 회원만 처음 한 번).
     * 옷·셀카·러닝량이 바뀌어도 다음 스냅숏까지는 예전 모습이다. 저장소를 쓸 수 없으면 매번 DB에서 읽는다.
     *
     * @param snapshot 스냅숏 식별자(예: PERSONAL:WEEKLY:{집계 시각 epoch 밀리초}). 새 스냅숏은 새 식별자다
     * @param memberIds 회원 ID(null은 건너뛴다)
     * @return 넘긴 회원 모두의 모습
     */
    public Map<Long, RunnerLook> snapshotLooksOf(String snapshot, Collection<Long> memberIds) {
        Set<Long> ids = new LinkedHashSet<>();
        memberIds.stream().filter(Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, RunnerLook> stored = snapshotStore.find(snapshot, ids);
        Set<Long> missing = new LinkedHashSet<>(ids);
        missing.removeAll(stored.keySet());
        Map<Long, RunnerLook> read = looksOf(missing);
        if (!read.isEmpty()) {
            snapshotStore.save(snapshot, read);
        }
        Map<Long, RunnerLook> looks = new LinkedHashMap<>();
        for (Long id : ids) {
            looks.put(id, stored.containsKey(id) ? stored.get(id) : read.get(id));
        }
        return looks;
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
