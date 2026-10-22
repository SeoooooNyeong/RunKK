package com.a207.runkk.reward;

import java.time.LocalDateTime;

/**
 * 적립 요청 한 건(설계 6.1.8 RewardWallet.accrue).
 *
 * @param reason 적립 사유
 * @param sourceId 근거 ID(기록 ID 또는 대결 ID)
 * @param baseAmount 상한 적용 전 적립량
 * @param at 적립 시각(하루 상한의 "오늘")
 */
public record AccrualRequest(
        AccrualReason reason, String sourceId, int baseAmount, LocalDateTime at) {

    public AccrualRequest {
        if (reason == null || sourceId == null || sourceId.isBlank() || at == null) {
            throw new IllegalArgumentException("적립 사유·근거·시각이 비었다");
        }
    }
}
