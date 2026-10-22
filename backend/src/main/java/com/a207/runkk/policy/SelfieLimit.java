package com.a207.runkk.policy;

/**
 * 셀카 생성 하루 한도(설계 6.1.9, D-41).
 *
 * @param perDay 하루 횟수
 */
public record SelfieLimit(int perDay) {}
