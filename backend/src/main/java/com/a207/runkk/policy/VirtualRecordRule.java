package com.a207.runkk.policy;

import com.a207.runkk.common.Distance;
import java.util.List;

/**
 * 가상 기록 생성 규칙(설계 6.1.9).
 *
 * @param openLowScore 브론즈 아래 열린 구간을 닫는 점수
 * @param openHighScore 마스터 위 열린 구간을 닫는 점수
 * @param paceOffsets 페이스 오프셋 5개
 * @param distances 생성 거리
 */
public record VirtualRecordRule(
        double openLowScore,
        double openHighScore,
        List<Double> paceOffsets,
        List<Distance> distances) {

    public VirtualRecordRule {
        paceOffsets = List.copyOf(paceOffsets);
        distances = List.copyOf(distances);
    }
}
