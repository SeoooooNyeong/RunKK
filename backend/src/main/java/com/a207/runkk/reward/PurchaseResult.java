package com.a207.runkk.reward;

/**
 * 구매 결과(자료사전 구매결과).
 *
 * @param itemId 산 아이템
 * @param balance 구매 뒤 잔액
 */
public record PurchaseResult(long itemId, int balance) {}
