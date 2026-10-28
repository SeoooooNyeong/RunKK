package com.a207.runkk.reward;

import java.time.LocalDateTime;

/**
 * 적립 근거 한 건: 누구에게(memberId) 무엇 때문에(reason, sourceId) 언제 일어난 사건(at)인가. 수신자(RewardService)와 일 단위
 * 보정(RewardReconcileService)이 같은 근거를 accrueWithRetry에 넘긴다(코드 리뷰 3차 B-M1·B-M2). 적립량은 그때의 RewardRule로
 * 정해지므로 담지 않는다.
 *
 * @param memberId 적립받을 회원 ID
 * @param reason 적립 사유
 * @param sourceId 근거 ID(기록 ID 또는 대결 ID)
 * @param at 사건 발생 시각(하루 상한의 "그날")
 */
record AccrualSource(long memberId, AccrualReason reason, String sourceId, LocalDateTime at) {

    /** 상한 적용 전 적립량 baseAmount로 지갑에 넘길 적립 요청을 만든다. */
    AccrualRequest request(int baseAmount) {
        return new AccrualRequest(reason, sourceId, baseAmount, at);
    }
}
