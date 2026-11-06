package com.a207.runkk.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.battle.Verdict;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.PlacementVerdict;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 배치고사·티어 표시 REST 계약(05-03 5.7.3.3, P1~P8). 새 항목은 기존 항목 뒤에 이름 그대로 붙고(옛 앱 호환, D-510), 새 응답
 * PlacementResponse는 표의 항목만 이 순서로 가진다.
 */
@org.junit.jupiter.api.DisplayName("배치고사 REST 계약 항목(05-03 5.7.3.3)")
class PlacementContractTest {

    @ParameterizedTest(name = "TC-5706 {0}")
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

    @ParameterizedTest(name = "TC-5707 {0}")
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

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "TC-5703|member.PlacementStatus|IN_PROGRESS,COMPLETED,EXEMPT",
                "TC-5705|member.PlacementVerdict|WIN,LOSE,DRAW",
                "TC-5704|battle.PlacementRoundState|COUNTED,NOT_COUNTED,PENDING",
            })
    void newEnums_haveTheContractValuesInOrder(String tc, String type, String values)
            throws Exception {
        Object[] constants = Class.forName("com.a207.runkk." + type).getEnumConstants();

        assertThat(Arrays.stream(constants).map(c -> ((Enum<?>) c).name()))
                .containsExactly(values.split(","));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5705 member.PlacementVerdict와 battle.Verdict 값이 같다(이름으로 옮기므로 하나라도 다르면 실패, H-62)")
    void placementVerdict_matchesBattleVerdict() {
        assertThat(Arrays.stream(PlacementVerdict.values()).map(Enum::name))
                .containsExactlyElementsOf(
                        Arrays.stream(Verdict.values()).map(Enum::name).toList());
    }

    /**
     * BE-0 전(df211dc5) 계약 기준 파일의 항목 전부. 배치고사 변경은 이 뒤에 붙이기만 한다: 삭제·이름 변경·순서 변경 0(옛 앱 호환 J24-11,
     * D-510).
     */
    @ParameterizedTest(name = "TC-5708 {0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "web.battle.BattleDtos$MatchAcceptanceResponse"
                        + "|waiting,distanceMeters,defaultDistanceMeters",
                "web.battle.BattleDtos$BattleResultResponse"
                        + "|battleId,type,pending,retryUpload,verdict,corrected,myReachSeconds,"
                        + "opponentReachSeconds,rows,weekly,rewardPoints,newRecord,opponentName,"
                        + "recordId,totalRewardPoints,evaluation,opponentGender,celebrityFigure",
                "web.battle.BattleDtos$ActiveBattleResponse"
                        + "|battleId,type,status,distanceMeters,opponentName,startAt,"
                        + "opponentGender,celebrityFigure",
                "web.battle.BattleDtos$Entry"
                        + "|battleId,type,opponentName,distanceMeters,verdict,playedAt,newRecord,"
                        + "gapSeconds,sourceLabel",
                "web.record.RecordDtos$Entry"
                        + "|rank,subjectId,name,tier,distanceMeters,shownGender",
                "web.record.RecordDtos$DashboardResponse"
                        + "|baseDate,unit,todayMeters,todaySeconds,todayRuns,totalMeters,"
                        + "totalSeconds,totalRuns,landmark,streakDays,grass,grassLevels,"
                        + "weeklyAchievement,weekly,crew,calendar,rewardBalance,monthRuns,"
                        + "monthMeters,streakWeeks",
            })
    void placementContract_onlyAddsFieldsAndKeepsRankedOnly(String type, String before)
            throws Exception {
        List<String> old = List.of(before.split(","));

        assertThat(componentNames(type).subList(0, old.size())).containsExactlyElementsOf(old);
        assertThat(ReasonCode.valueOf("RANKED_ONLY")).as("옛 앱 문구용 사유 코드는 남는다(D-478)").isNotNull();
        try (InputStream in =
                getClass().getResourceAsStream("/contract/app/enums/ReasonCode.txt")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines())
                    .contains("RANKED_ONLY");
        }
    }

    private static List<String> componentNames(String type) throws ClassNotFoundException {
        RecordComponent[] components =
                Class.forName("com.a207.runkk." + type).getRecordComponents();
        return Arrays.stream(components).map(RecordComponent::getName).toList();
    }
}
