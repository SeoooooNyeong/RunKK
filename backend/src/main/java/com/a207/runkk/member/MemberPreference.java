package com.a207.runkk.member;

import com.a207.runkk.common.DistanceUnit;
import com.a207.runkk.common.RecordVisibility;

/**
 * analysis·profile이 엔티티 대신 읽는 회원 설정 투영(설계 6.1.2).
 *
 * @param unit 거리 단위
 * @param recordVisibility 기록 공개 범위
 * @param courseMapShared 코스 지도 공유
 * @param profileVisibility 개인정보 공개
 * @param llmAgreed LLM 전송 동의
 */
public record MemberPreference(
        DistanceUnit unit,
        RecordVisibility recordVisibility,
        boolean courseMapShared,
        ProfileVisibility profileVisibility,
        boolean llmAgreed) {}
