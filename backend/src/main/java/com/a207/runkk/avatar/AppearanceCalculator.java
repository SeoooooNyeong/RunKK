package com.a207.runkk.avatar;

import com.a207.runkk.common.Distance;
import com.a207.runkk.policy.BodyShapeRule;
import com.a207.runkk.policy.SkinToneRule;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 러닝 기록으로 체형·피부색을 계산한다(설계 6.1.8, 6.2 A-16, FR-163, FR-164). 상태가 없다. */
@Component
public class AppearanceCalculator {

    /**
     * 쉰 날(오늘 − 마지막 유효 러닝 날짜) &gt; rule.restDaysBeforeDecline이면 이전 체형.lowerOneStep(), 아니면
     * rule.stageFor(최근 7일 횟수). 유효 러닝이 없으면 쉰 날은 무한이다.
     */
    public BodyShape bodyShape(AppearanceInput in, BodyShapeRule rule) {
        long restDays =
                in.lastRun()
                        .map(last -> ChronoUnit.DAYS.between(last.toLocalDate(), in.today()))
                        .orElse(Long.MAX_VALUE);
        if (restDays > rule.restDaysBeforeDecline()) {
            return in.previous().lowerOneStep();
        }
        return BodyShape.ofStage(rule.stageFor(in.runsIn7Days()));
    }

    /** 시간대별 개수가 가장 큰 톤. 같으면 가장 최근 러닝의 시간대. 시작 일시가 하나 이상이어야 한다. */
    public SkinTone skinTone(List<LocalDateTime> recent10Starts, SkinToneRule rule) {
        if (recent10Starts.isEmpty()) {
            throw new IllegalArgumentException("피부색을 정할 러닝이 없다");
        }
        Map<SkinTone, Integer> counts = new EnumMap<>(SkinTone.class);
        for (LocalDateTime start : recent10Starts) {
            counts.merge(toneOf(start, rule), 1, Integer::sum);
        }
        int best = counts.values().stream().max(Integer::compare).orElseThrow();
        List<SkinTone> top =
                counts.entrySet().stream()
                        .filter(e -> e.getValue() == best)
                        .map(Map.Entry::getKey)
                        .toList();
        if (top.size() == 1) {
            return top.get(0);
        }
        LocalDateTime latest = recent10Starts.stream().max(Comparator.naturalOrder()).orElseThrow();
        return toneOf(latest, rule);
    }

    /**
     * 피부톤 근거 대표 시각(시, 0~23, FR-164 "주로 오전 7시에", W8). 시작 일시 중 tone 시간대에 속한 것들에서 가장 많은 시이고, 같으면 그중 가장
     * 최근 러닝의 시다. 그 시간대 러닝이 없으면 비어 있다.
     */
    public Optional<Integer> typicalStartHour(
            List<LocalDateTime> recentStarts, SkinTone tone, SkinToneRule rule) {
        List<LocalDateTime> inTone =
                recentStarts.stream().filter(start -> toneOf(start, rule) == tone).toList();
        if (inTone.isEmpty()) {
            return Optional.empty();
        }
        Map<Integer, Integer> counts = new java.util.HashMap<>();
        inTone.forEach(start -> counts.merge(start.getHour(), 1, Integer::sum));
        int best = counts.values().stream().max(Integer::compare).orElseThrow();
        return inTone.stream()
                .filter(start -> counts.get(start.getHour()) == best)
                .max(Comparator.naturalOrder())
                .map(LocalDateTime::getHour);
    }

    /** 러닝량이 100이 되는 최근 7일 유효 거리(m). */
    static final long FULL_RUN_AMOUNT_METERS = 30_000;

    /**
     * W9 Rive 피부톤 입력 러닝량 0~100(S15P31A207-300): min(100, ⌊최근 7일 유효 거리 × 100 / 30km⌋). 낮으면 밝게, 높으면
     * 어둡게 그린다(PoC 규칙).
     */
    public static int runAmount(Distance recentSevenDays) {
        return (int) Math.min(100, recentSevenDays.meters() * 100 / FULL_RUN_AMOUNT_METERS);
    }

    private static SkinTone toneOf(LocalDateTime start, SkinToneRule rule) {
        return SkinTone.ofBand(rule.toneOf(start.toLocalTime()));
    }
}
