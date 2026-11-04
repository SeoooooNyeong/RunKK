package com.a207.runkk.member;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 시험용 배치 진행 저장소. 잠금은 흉내 내지 않고 어떤 연산이 불렸는지만 남긴다. */
public class InMemoryPlacementProgressRepository implements PlacementProgressRepository {

    private final Map<Long, PlacementProgress> rows = new HashMap<>();
    private final List<String> calls = new ArrayList<>();

    @Override
    public Optional<PlacementProgress> findById(long memberId) {
        calls.add("findById");
        return Optional.ofNullable(rows.get(memberId));
    }

    @Override
    public PlacementProgress lockOrCreate(long memberId) {
        calls.add("lockOrCreate");
        return rows.computeIfAbsent(memberId, PlacementProgress::start);
    }

    @Override
    public Optional<PlacementProgress> findByIdForUpdate(long memberId) {
        calls.add("findByIdForUpdate");
        return Optional.ofNullable(rows.get(memberId));
    }

    @Override
    public PlacementProgress save(PlacementProgress progress) {
        calls.add("save");
        rows.put(progress.getMemberId(), progress);
        return progress;
    }

    @Override
    public void deleteByMemberId(long memberId) {
        calls.add("deleteByMemberId");
        rows.remove(memberId);
    }

    @Override
    public boolean existsRound(long memberId, long battleId) {
        return Optional.ofNullable(rows.get(memberId))
                .map(p -> p.hasCounted(battleId))
                .orElse(false);
    }

    /** 시험이 진행 행을 바로 넣는다. */
    public void put(PlacementProgress progress) {
        rows.put(progress.getMemberId(), progress);
    }

    public boolean has(long memberId) {
        return rows.containsKey(memberId);
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }
}
