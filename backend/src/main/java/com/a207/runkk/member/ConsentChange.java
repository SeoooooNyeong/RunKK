package com.a207.runkk.member;

import java.time.LocalDateTime;

/**
 * 동의 한 건을 바꾼 결과. 이전·이후 값을 감사·사건에 쓴다(설계 6.1.2).
 *
 * @param item 동의 항목
 * @param before 이전 동의 여부
 * @param after 이후 동의 여부
 * @param at 변경 일시
 */
public record ConsentChange(ConsentItem item, boolean before, boolean after, LocalDateTime at) {}
