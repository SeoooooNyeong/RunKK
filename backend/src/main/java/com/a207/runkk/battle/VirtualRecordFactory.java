package com.a207.runkk.battle;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.policy.TierBoundary;
import com.a207.runkk.policy.VirtualRecordRule;
import com.a207.runkk.running.RunningScore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * 가상 기록 100개를 만든다(설계 6.1.6, A-21, srs.md 6.3 7.5). 티어 5 × 거리 4 × 페이스 오프셋 5. 티어 가운데 점수를 VDOT 식으로 거꾸로
 * 풀어 기준 페이스를 구하고(D-12), 가상 닉네임은 회원 닉네임·이미 붙인 이름과 겹치지 않게 다시 뽑는다(D-45).
 */
@Component
public class VirtualRecordFactory {

    /** 가상 기록을 만드는 티어(브론즈~마스터). */
    static final List<Tier> TIERS =
            List.of(Tier.BRONZE, Tier.SILVER, Tier.GOLD, Tier.PLATINUM, Tier.MASTER);

    private static final int NAME_MIN = 2;
    private static final int NAME_MAX = 20;
    private static final int SUFFIX_BOUND = 1_000;
    private static final int MAX_ATTEMPTS = 1_000;
    private static final List<String> ADJECTIVES =
            List.of(
                    "바람같은", "새벽의", "꾸준한", "번개", "한강", "푸른", "달빛", "씩씩한", "느긋한", "단단한", "가벼운",
                    "빛나는");
    private static final List<String> NOUNS =
            List.of("치타", "여우", "토끼", "러너", "고래", "참새", "늑대", "다람쥐", "사슴", "돌고래", "표범", "제비");

    private final RandomGenerator random;

    public VirtualRecordFactory(RandomGenerator battleRandom) {
        this.random = battleRandom;
    }

    /** 티어 5 × rule.distances(랜덤 대결 거리) × rule.paceOffsets. 기본 규칙이면 100개. */
    public List<VirtualRecord> generate(
            TierBoundary boundary, VirtualRecordRule rule, Predicate<String> nicknameTaken) {
        List<StandardDistance> distances = randomBattleDistances(rule);
        Set<String> used = new HashSet<>();
        List<VirtualRecord> result = new ArrayList<>();
        for (Tier tier : TIERS) {
            RunningScore mid = new RunningScore(boundary.midScoreOf(tier, rule));
            for (StandardDistance d : distances) {
                Pace basePace = mid.paceFor(d.distance());
                for (double offset : rule.paceOffsets()) {
                    Duration finish = finishTime(basePace, offset, d.distance());
                    String name = uniqueName(nicknameTaken, used);
                    result.add(new VirtualRecord(tier, d, name, finish));
                }
            }
        }
        return result;
    }

    /** 규칙이 만드는 가상 기록 수 = 티어 5 × 랜덤 대결 거리 수 × 페이스 오프셋 수(기본 100). */
    static long expectedCount(VirtualRecordRule rule) {
        return (long) TIERS.size() * randomBattleDistances(rule).size() * rule.paceOffsets().size();
    }

    private static List<StandardDistance> randomBattleDistances(VirtualRecordRule rule) {
        List<StandardDistance> distances = new ArrayList<>();
        for (Distance d : rule.distances()) {
            matching(d).filter(StandardDistance::isRandomBattle).ifPresent(distances::add);
        }
        return distances;
    }

    private static Optional<StandardDistance> matching(Distance d) {
        for (StandardDistance s : StandardDistance.values()) {
            if (s.distance().equals(d)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** 완주 시간 = 거리 × 페이스 × (1 + 오프셋). */
    static Duration finishTime(Pace basePace, double offset, Distance d) {
        double secondsPerKm = basePace.secondsPerKm() * (1 + offset);
        return Duration.ofMillis(Math.round(secondsPerKm * d.meters()));
    }

    private String uniqueName(Predicate<String> nicknameTaken, Set<String> used) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String name = randomName(attempt > 0);
            if (name.length() >= NAME_MIN
                    && name.length() <= NAME_MAX
                    && !used.contains(name)
                    && !nicknameTaken.test(name)) {
                used.add(name);
                return name;
            }
        }
        throw new IllegalStateException("겹치지 않는 가상 닉네임을 만들지 못했다");
    }

    private String randomName(boolean withSuffix) {
        String name =
                ADJECTIVES.get(random.nextInt(ADJECTIVES.size()))
                        + NOUNS.get(random.nextInt(NOUNS.size()));
        return withSuffix ? name + random.nextInt(SUFFIX_BOUND) : name;
    }
}
