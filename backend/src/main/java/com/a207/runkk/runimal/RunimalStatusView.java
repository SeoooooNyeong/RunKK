package com.a207.runkk.runimal;

import java.util.List;
import java.util.Optional;

/**
 * Runimal 유형 현황(자료사전 유형현황, FR-151~FR-154).
 *
 * @param provisionalType 임시 유형, 없으면 null
 * @param confirmedType 확정 유형, 없으면 null
 * @param validRunCount 유효 러닝 횟수(직전 반영 순번)
 * @param remainingRuns 다음 판정까지 남은 횟수
 * @param card 최신 유형 카드, 유형이 없으면 null
 * @param allTypes 16유형 목록
 */
public record RunimalStatusView(
        RunimalType provisionalType,
        RunimalType confirmedType,
        int validRunCount,
        int remainingRuns,
        RunimalCardView card,
        List<RunimalType> allTypes) {

    public RunimalStatusView {
        allTypes = List.copyOf(allTypes);
    }

    public Optional<RunimalCardView> cardValue() {
        return Optional.ofNullable(card);
    }
}
