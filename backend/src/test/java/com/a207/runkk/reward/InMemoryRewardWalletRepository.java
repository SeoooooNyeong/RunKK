package com.a207.runkk.reward;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 시험용 지갑 저장소(14장 단위: 인메모리 Repository). */
public final class InMemoryRewardWalletRepository implements RewardWalletRepository {

    private final Map<Long, RewardWallet> wallets = new HashMap<>();
    private final java.util.Deque<RuntimeException> failures = new java.util.ArrayDeque<>();

    /** 다음 지갑 잠금 호출들이 차례로 이 예외를 던진다(수신자 실패 시험). */
    public synchronized void failNext(RuntimeException... errors) {
        failures.addAll(java.util.List.of(errors));
    }

    @Override
    public synchronized RewardWallet findOrCreateForUpdate(long memberId) {
        if (!failures.isEmpty()) {
            throw failures.poll();
        }
        return wallets.computeIfAbsent(memberId, RewardWallet::open);
    }

    @Override
    public synchronized Optional<RewardWallet> findByMember(long memberId) {
        return Optional.ofNullable(wallets.get(memberId));
    }

    @Override
    public synchronized void save(RewardWallet wallet) {
        wallets.put(wallet.memberId(), wallet);
    }

    @Override
    public synchronized void deleteByMember(long memberId) {
        wallets.remove(memberId);
    }
}
