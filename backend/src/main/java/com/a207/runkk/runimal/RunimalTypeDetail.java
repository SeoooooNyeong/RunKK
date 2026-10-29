package com.a207.runkk.runimal;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 유형 상세(설계 5.6.2, FR-226, D-246).
 *
 * @param type 유형(이름·아이콘·한 줄 설명·4축은 상수가 가진다)
 * @param traits 특징 3줄
 * @param sharePercent 러너 중 이 유형 비율(%), 확정자가 없거나 아직 계산 전이면 null
 * @param computedAt 비율 계산 시각(아직 계산 전이면 null)
 */
public record RunimalTypeDetail(
        RunimalType type, List<String> traits, Integer sharePercent, LocalDateTime computedAt) {}
