package com.a207.runkk.policy;

import com.a207.runkk.common.Distance;

/**
 * 랜드마크 하나(설계 6.1.9, srs.md 3.3.1). key는 배경 해금의 고정 식별자다: 순번·이름은 표 수정·번역 때 어긋나므로 쓰지 않는다(D-107). 형식(영문
 * 대문자·숫자·밑줄)과 표 안 고유는 SettingKey.LANDMARKS 검사가 지킨다.
 *
 * @param key 고정 키(영문 대문자·숫자·밑줄, 표 안에서 고유)
 * @param stage 단계
 * @param name 이름
 * @param distance 넘었다고 보는 누적 거리(왕복 랜드마크는 srs.md 3.3.1의 왕복 거리)
 * @param roundTrip 왕복 기준인가
 */
public record Landmark(
        String key, LandmarkStage stage, String name, Distance distance, boolean roundTrip) {}
