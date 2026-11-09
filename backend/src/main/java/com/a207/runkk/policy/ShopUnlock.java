package com.a207.runkk.policy;

/**
 * 상점 아이템 해금 조건(W8, S15P31A207-299, 사용자 결정 A1). 조건을 채우기 전에는 잠겨 살 수 없고, 앱은 text를 그대로 보인다.
 *
 * @param kind 조건 종류
 * @param threshold 기준값(종류별 단위), 0보다 큼
 * @param text 화면 문구(예 "하프 완주하면 열려요"), 1~40자
 */
public record ShopUnlock(UnlockKind kind, long threshold, String text) {

    /** 문구 최대 길이. */
    public static final int TEXT_MAX = 40;

    public ShopUnlock {
        if (kind == null) {
            throw new IllegalArgumentException("해금 조건 종류가 비었다");
        }
        if (threshold <= 0) {
            throw new IllegalArgumentException("해금 기준은 0보다 커야 한다: " + threshold);
        }
        if (text == null || text.isBlank() || text.length() > TEXT_MAX) {
            throw new IllegalArgumentException("해금 문구는 1~40자");
        }
    }
}
