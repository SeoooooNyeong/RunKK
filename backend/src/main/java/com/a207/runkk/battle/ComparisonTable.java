package com.a207.runkk.battle;

import com.a207.runkk.running.RunComparison;
import java.util.List;

/**
 * 대결 결과 비교표(설계 6.1.6, FR-093). type.comparisonItems() 순서로 줄을 만든다. mine은
 * RunQueryService.comparisonOf 값 세 항목을 모두 쓴다(D-41).
 *
 * @param rows 비교 줄
 */
public record ComparisonTable(List<Row> rows) {

    public ComparisonTable {
        rows = List.copyOf(rows);
    }

    public static ComparisonTable of(
            BattleType type, RunComparison mine, ComparisonValues theirs, BattleParticipant me) {
        return new ComparisonTable(
                type.comparisonItems().stream()
                        .map(item -> new Row(item, item.mine(mine, me), item.theirs(theirs)))
                        .toList());
    }

    /**
     * 비교 한 줄.
     *
     * @param item 항목
     * @param mine 본인 값 문구
     * @param theirs 상대 값 문구
     */
    public record Row(ComparisonItem item, String mine, String theirs) {}
}
