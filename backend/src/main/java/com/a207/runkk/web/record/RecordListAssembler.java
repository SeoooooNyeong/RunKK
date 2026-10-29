package com.a207.runkk.web.record;

import com.a207.runkk.analysis.RecordListView;
import com.a207.runkk.analysis.RecordQueryService;
import com.a207.runkk.analysis.RecordRow;
import com.a207.runkk.battle.BattleHistoryService;
import com.a207.runkk.battle.BattleOutcome;
import com.a207.runkk.common.DateRange;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 기록 목록 응답을 조합한다(설계 5.6.2 web.record.RecordListAssembler, D-233, 12.2 W1). analysis는 battle을
 * import하지 않으므로 대결 기록 행의 종류·판정·보류를 표현 계층이 BattleHistoryService.outcomesOf 한 번으로 붙인다(D-33
 * BattleResultAssembler와 같은 방식, N+1 없음).
 */
@Component
class RecordListAssembler {

    private final RecordQueryService recordQueryService;
    private final BattleHistoryService battleHistoryService;

    RecordListAssembler(
            RecordQueryService recordQueryService, BattleHistoryService battleHistoryService) {
        this.recordQueryService = recordQueryService;
        this.battleHistoryService = battleHistoryService;
    }

    RecordDtos.RecordListResponse assemble(long viewerId, long ownerId, DateRange range) {
        RecordListView view = recordQueryService.list(viewerId, ownerId, range);
        List<Long> battleIds =
                view.runs().stream().map(RecordRow::battleId).filter(Objects::nonNull).toList();
        Map<Long, BattleOutcome> outcomes = battleHistoryService.outcomesOf(ownerId, battleIds);
        return new RecordDtos.RecordListResponse(
                view.ownerId(),
                view.runs().stream()
                        .map(
                                row ->
                                        RecordDtos.RunSummaryDto.of(
                                                row,
                                                row.battleId() == null
                                                        ? null
                                                        : outcomes.get(row.battleId())))
                        .toList());
    }
}
