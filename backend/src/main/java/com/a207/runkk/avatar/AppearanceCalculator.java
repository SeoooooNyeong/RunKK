package com.a207.runkk.avatar;

import com.a207.runkk.policy.BodyShapeRule;
import com.a207.runkk.policy.SkinToneRule;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    private static SkinTone toneOf(LocalDateTime start, SkinToneRule rule) {
        return SkinTone.ofBand(rule.toneOf(start.toLocalTime()));
    }
}
