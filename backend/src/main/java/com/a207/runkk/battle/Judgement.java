package com.a207.runkk.battle;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 대결 판정 결과(설계 6.1.6).
 *
 * @param verdicts 쪽마다 판정
 * @param winner 이긴 쪽, 무승부면 null
 * @param corrected 이미 끝난 판정이 바뀌었는가(FR-103)
 * @param at 판정 시각
 */
public record Judgement(
        Map<Side, Verdict> verdicts, Side winner, boolean corrected, LocalDateTime at) {

    public Judgement {
        verdicts = Map.copyOf(verdicts);
    }

    public Optional<Side> winnerSide() {
        return Optional.ofNullable(winner);
    }

    /** 그 쪽의 판정. 참가하지 않은 쪽이면 비어 있다. */
    public Optional<Verdict> verdictOf(Side side) {
        return Optional.ofNullable(verdicts.get(side));
    }
}
