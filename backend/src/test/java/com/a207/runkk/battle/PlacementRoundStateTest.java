package com.a207.runkk.battle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 판 결과 상태(05-21 D-487, v0.31 R89): 위에서 처음 맞는 것. */
@org.junit.jupiter.api.DisplayName("PlacementRoundState 판 결과 상태(D-487)")
class PlacementRoundStateTest {

    private static PlacementRoundCheck finishedValid() {
        return new PlacementRoundCheck(true, false, false, true, true, false, false);
    }

    private static PlacementRoundCheck voidRound() {
        return new PlacementRoundCheck(true, false, true, true, true, false, false);
    }

    private static PlacementRoundCheck running() {
        return new PlacementRoundCheck(false, false, false, false, null, false, false);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5752 이미 셌으면 COUNTED(그 뒤 정정·완료와 상관없음)")
    void counted_isCounted() {
        assertThat(PlacementRoundState.of(finishedValid(), true, true))
                .isEqualTo(PlacementRoundState.COUNTED);
        assertThat(PlacementRoundState.of(voidRound(), true, false))
                .isEqualTo(PlacementRoundState.COUNTED);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5753 세지 않는 판(포기·무효·만료·취소)은 NOT_COUNTED")
    void void_isNotCounted() {
        assertThat(PlacementRoundState.of(voidRound(), false, false))
                .isEqualTo(PlacementRoundState.NOT_COUNTED);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5754 끝났는데 배치고사가 이미 끝났으면(3판째 확정 뒤 판정된 판) NOT_COUNTED(R89)")
    void finishedAfterCompletion_isNotCounted() {
        assertThat(PlacementRoundState.of(finishedValid(), false, true))
                .isEqualTo(PlacementRoundState.NOT_COUNTED);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5755 끝났지만 아직 셈 반영 전이면 PENDING")
    void finishedNotYetCounted_isPending() {
        assertThat(PlacementRoundState.of(finishedValid(), false, false))
                .isEqualTo(PlacementRoundState.PENDING);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5756 아직 끝나지 않았으면(진행 중·보류) PENDING, 완료 뒤라도 판정 전이면 PENDING")
    void notFinished_isPending() {
        assertThat(PlacementRoundState.of(running(), false, false))
                .isEqualTo(PlacementRoundState.PENDING);
        assertThat(PlacementRoundState.of(running(), false, true))
                .isEqualTo(PlacementRoundState.PENDING);
    }

    @ParameterizedTest(name = "TC-5810 기초 경로 {0}")
    @CsvSource({
        "counted, true, false, false, false, COUNTED",
        "void, false, true, true, false, NOT_COUNTED",
        "completed, false, false, true, true, NOT_COUNTED",
        "finished, false, false, true, false, PENDING",
        "running, false, false, false, false, PENDING"
    })
    void basisPaths_fiveCases(
            String path,
            boolean counted,
            boolean forfeited,
            boolean finished,
            boolean completed,
            PlacementRoundState expected) {
        PlacementRoundCheck check =
                new PlacementRoundCheck(
                        finished, false, forfeited, finished, finished ? true : null, false, false);
        assertThat(PlacementRoundState.of(check, counted, completed)).as(path).isEqualTo(expected);
    }
}
