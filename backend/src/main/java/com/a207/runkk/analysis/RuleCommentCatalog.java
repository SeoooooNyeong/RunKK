package com.a207.runkk.analysis;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 규칙 기반 코멘트 문구 20개(설계 6.1.4 RuleCommentCatalog, FR-066). (지표 × Trend) 9개 키마다 2개, 모두 정체일 때 2개다. 같은
 * 요약에는 언제나 같은 문구를 고른다(요약 순번으로 변형을 고른다).
 */
@Component
public class RuleCommentCatalog {

    /** 문구 묶음 이름(지표). */
    enum Metric {
        PACE,
        DISTANCE,
        FREQUENCY
    }

    private static final Map<Metric, Map<Trend, List<String>>> PHRASES =
            Map.of(
                    Metric.PACE,
                    Map.of(
                            Trend.IMPROVING,
                            List.of("페이스가 빨라지고 있어요. 지금 리듬을 이어 가요!", "최근 기록이 더 가벼워졌어요. 좋은 흐름이에요."),
                            Trend.STEADY,
                            List.of("페이스가 안정적이에요. 꾸준함이 실력이에요.", "비슷한 페이스를 잘 지키고 있어요."),
                            Trend.DECLINING,
                            List.of(
                                    "페이스가 조금 느려졌어요. 오늘은 가볍게 몸을 풀어 봐요.",
                                    "무리하지 말고 회복 러닝으로 컨디션을 챙겨요.")),
                    Metric.DISTANCE,
                    Map.of(
                            Trend.IMPROVING,
                            List.of("한 번에 달리는 거리가 늘었어요. 멋져요!", "거리가 꾸준히 늘고 있어요. 다음 목표도 해낼 수 있어요."),
                            Trend.STEADY,
                            List.of("비슷한 거리를 꾸준히 달리고 있어요.", "익숙한 거리를 잘 지키고 있어요. 가끔 조금 더 멀리 가 봐요."),
                            Trend.DECLINING,
                            List.of(
                                    "최근 거리가 조금 줄었어요. 짧게라도 자주 달려 봐요.",
                                    "거리는 줄었지만 괜찮아요. 몸 상태에 맞춰 천천히 늘려요.")),
                    Metric.FREQUENCY,
                    Map.of(
                            Trend.IMPROVING,
                            List.of("달리는 날이 많아졌어요. 습관이 자리 잡고 있어요!", "꾸준히 자주 달리고 있어요. 대단해요."),
                            Trend.STEADY,
                            List.of("일정한 간격으로 잘 달리고 있어요.", "러닝 리듬이 안정적이에요."),
                            Trend.DECLINING,
                            List.of(
                                    "최근 달린 날이 줄었어요. 이번 주에 한 번 더 나가 볼까요?",
                                    "바쁜 한 주였나 봐요. 짧은 러닝으로 다시 시작해요.")));

    private static final List<String> ALL_STEADY =
            List.of("지금처럼만 달려도 충분히 잘하고 있어요.", "기록이 고르게 유지되고 있어요. 다음 러닝도 응원해요!");

    /** 페이스 → 거리 → 빈도 순으로 정체가 아닌 첫 지표의 문구, 모두 정체면 정체 문구. */
    public String pick(FitnessSummary summary) {
        int variant = Math.floorMod(summary.toRun(), 2);
        for (Metric metric : Metric.values()) {
            Trend trend = trendOf(metric, summary);
            if (trend != Trend.STEADY) {
                return PHRASES.get(metric).get(trend).get(variant);
            }
        }
        return ALL_STEADY.get(variant);
    }

    /** 문구 수(FR-066 20개). */
    int size() {
        int count = ALL_STEADY.size();
        for (Map<Trend, List<String>> byTrend : PHRASES.values()) {
            count += byTrend.values().stream().mapToInt(List::size).sum();
        }
        return count;
    }

    private static Trend trendOf(Metric metric, FitnessSummary summary) {
        return switch (metric) {
            case PACE -> summary.paceTrend();
            case DISTANCE -> summary.distanceTrend();
            case FREQUENCY -> summary.frequencyTrend();
        };
    }
}
