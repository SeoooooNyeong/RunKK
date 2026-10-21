package com.a207.runkk.reward;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 시험용 지갑 저장소(14장 단위: 인메모리 Repository). */
public final class InMemoryRewardWalletRepository implements RewardWalletRepository {

    private final Map<Long, RewardWallet> wallets = new HashMap<>();

    @Override
    public synchronized RewardWallet findOrCreateForUpdate(long memberId) {
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
