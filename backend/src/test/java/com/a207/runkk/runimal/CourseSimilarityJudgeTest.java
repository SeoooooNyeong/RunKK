package com.a207.runkk.runimal;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.policy.CourseSimilarityRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CourseSimilarityJudge(14장 단위, A-15, Q-80: 포인트 80% 이상이 이전 코스 50m 안이면 같은 코스). */
@DisplayName("CourseSimilarityJudge 코스 동일성")
class CourseSimilarityJudgeTest {

    private static final CourseSimilarityRule RULE = new CourseSimilarityRule(0.8, 50, 30);
    private static final double START_LAT = 37.5665;
    private static final double START_LON = 126.9780;

    /** 위도 0.0001도 ≈ 11m. */
    private static final double STEP = 0.0001;

    private final CourseSimilarityJudge judge = new CourseSimilarityJudge();

    /** 북쪽으로 곧게 n점, 경도를 lonShift만큼 옮긴 길. */
    private static List<GeoPoint> northLine(int n, double lonShift) {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            points.add(new GeoPoint(START_LAT + i * STEP, START_LON + lonShift));
        }
        return points;
    }

    @Test
    @DisplayName("같은 길을 30m 옆으로 달리면 같은 코스")
    void sameRoadShifted_isSame() {
        List<GeoPoint> previous = northLine(100, 0);
        List<GeoPoint> today = northLine(100, 0.00034); // 약 30m 동쪽
        assertThat(judge.isSameCourse(today, List.of(previous), RULE)).isTrue();
    }

    @Test
    @DisplayName("200m 떨어진 길은 다른 코스")
    void farRoad_isDifferent() {
        List<GeoPoint> previous = northLine(100, 0);
        List<GeoPoint> today = northLine(100, 0.0023); // 약 200m 동쪽
        assertThat(judge.isSameCourse(today, List.of(previous), RULE)).isFalse();
    }

    @Test
    @DisplayName("겹치는 비율이 80% 미만이면 다른 코스, 이전 코스 중 하나만 맞아도 같은 코스")
    void coverageThreshold_andAnyPrevious() {
        List<GeoPoint> half = northLine(50, 0);
        List<GeoPoint> today = northLine(100, 0);
        assertThat(judge.isSameCourse(today, List.of(half), RULE)).isFalse();
        assertThat(judge.isSameCourse(today, List.of(half, northLine(100, 0)), RULE)).isTrue();
    }

    @Test
    @DisplayName("10m 간격 다시 표본화, 경로가 비면 같은 코스가 아니다")
    void resampleAndEmpty() {
        List<GeoPoint> dense = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            dense.add(new GeoPoint(START_LAT + i * STEP / 10, START_LON));
        }
        assertThat(CourseSimilarityJudge.resample(dense).size()).isBetween(9, 12);
        assertThat(judge.isSameCourse(List.of(), List.of(northLine(10, 0)), RULE)).isFalse();
        assertThat(judge.isSameCourse(northLine(10, 0), List.of(List.of()), RULE)).isFalse();
    }
}
