package com.a207.runkk.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 배치고사·티어 표시 REST 계약(05-03 5.7.3.3, P1~P8). 새 항목은 기존 항목 뒤에 이름 그대로 붙고(옛 앱 호환, D-510), 새 응답
 * PlacementResponse는 표의 항목만 이 순서로 가진다.
 */
@org.junit.jupiter.api.DisplayName("배치고사 REST 계약 항목(05-03 5.7.3.3)")
class PlacementContractTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "web.me.MeController$PlacementResponse"
                        + "|status,countedRounds,nextRound,provisionalTier,tier,rounds",
                "web.me.MeController$PlacementResponse$Round"
                        + "|round,battleId,battleDistanceMeters,distanceMeters,elapsedSeconds,"
                        + "averagePaceSecondsPerKm,verdict",
            })
    void newResponses_haveExactlyTheContractFields(String type, String fields) throws Exception {
        assertThat(componentNames(type)).containsExactly(fields.split(","));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "web.battle.BattleDtos$MatchAcceptanceResponse"
                        + "|waiting,distanceMeters,defaultDistanceMeters|placementRound,battleId",
                "web.battle.BattleDtos$BattleResultResponse"
                        + "|battleId,celebrityFigure|placementRound,placementState",
                "web.battle.BattleDtos$ActiveBattleResponse|battleId,celebrityFigure|placementRound",
                "web.battle.BattleDtos$Entry"
                        + "|battleId,sourceLabel|placementRound,opponentMemberId",
                "web.record.RecordDtos$Entry"
                        + "|rank,subjectId,name,tier,distanceMeters,shownGender|publicTier",
                "web.record.RecordDtos$DashboardResponse|baseDate,streakWeeks|latestRun",
            })
    void changedResponses_keepOldFieldsAndAppendNewOnes(String type, String kept, String added)
            throws Exception {
        List<String> names = componentNames(type);
        List<String> old = List.of(kept.split(","));
        List<String> appended = List.of(added.split(","));

        assertThat(names).containsAll(old);
        assertThat(names.subList(names.size() - appended.size(), names.size()))
                .containsExactlyElementsOf(appended);
        assertThat(names.indexOf(old.get(old.size() - 1)))
                .isLessThan(names.indexOf(appended.get(0)));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "member.PlacementStatus|IN_PROGRESS,COMPLETED,EXEMPT",
                "member.PlacementVerdict|WIN,LOSE,DRAW",
                "battle.PlacementRoundState|COUNTED,NOT_COUNTED,PENDING",
            })
    void newEnums_haveTheContractValuesInOrder(String type, String values) throws Exception {
        Object[] constants = Class.forName("com.a207.runkk." + type).getEnumConstants();

        assertThat(Arrays.stream(constants).map(c -> ((Enum<?>) c).name()))
                .containsExactly(values.split(","));
    }

    private static List<String> componentNames(String type) throws ClassNotFoundException {
        RecordComponent[] components =
                Class.forName("com.a207.runkk." + type).getRecordComponents();
        return Arrays.stream(components).map(RecordComponent::getName).toList();
    }
}
