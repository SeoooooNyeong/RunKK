package com.a207.runkk.policy;

import com.a207.runkk.common.Distance;
import java.time.Duration;

/**
 * Runimal 축 판정 기준(설계 6.1.9). 축 평가기(runimal.AxisEvaluator)가 축마다 자기 구성요소(companionRatio 등)를 읽는다. 설계의
 * threshold(RunimalAxis)는 policy → runimal 의존(순환)을 만들고, TagCategory로 받으면 7개 중 4개만 유효해 잘못된 입력을 막지 못해
 * 두지 않는다(1단계 리뷰 L13, 설계 갱신 요청 U1).
 *
 * @param companionRatio 동행 축 비율
 * @param shortDistance 단거리 기준 거리
 * @param shortRatio 거리 축 비율
 * @param sameCourseRatio 코스 축 비율
 * @param habitWindow 습관 축 시간 창
 * @param habitRatio 습관 축 비율
 */
public record RunimalRule(
        double companionRatio,
        Distance shortDistance,
        double shortRatio,
        double sameCourseRatio,
        Duration habitWindow,
        double habitRatio) {}
