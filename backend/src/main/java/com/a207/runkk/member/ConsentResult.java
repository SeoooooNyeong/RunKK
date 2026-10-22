package com.a207.runkk.member;

/**
 * 동의 주기·철회 결과(FR-013~FR-017).
 *
 * @param item 동의 항목
 * @param before 이전 동의 여부
 * @param after 이후 동의 여부
 */
public record ConsentResult(ConsentItem item, boolean before, boolean after) {}
