package com.a207.runkk.policy;

/**
 * 운영 기준값 변경 결과(설계 6.1.9, R29). policy 소유다.
 *
 * @param key 기준 항목
 * @param before 이전 값
 * @param after 새 값
 */
public record SettingChangeResult(SettingKey key, String before, String after) {}
