package com.a207.runkk.crew;

import com.a207.runkk.common.TagSet;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 성향 태그가 겹치는 수로 크루를 고른다(설계 6.1.7, FR-118). 일치도 1 이상만, 높은 순(같으면 최근 생성순), 상위 5개. Runimal 유형은 쓰지
 * 않는다(Q-39).
 */
@Component
public class TagOverlapRecommender implements CrewRecommender {

    /** 보여 줄 추천 수(FR-118). */
    static final int LIMIT = 5;

    @Override
    public List<Recommendation> recommend(TagSet runnerTags, List<CrewCandidate> candidates) {
        return candidates.stream()
                .map(c -> new Recommendation(c, runnerTags.overlapCount(c.tags())))
                .filter(r -> r.matchCount() > 0)
                .sorted(
                        Comparator.comparingInt(Recommendation::matchCount)
                                .reversed()
                                .thenComparing(
                                        r -> r.crew().createdAt(), Comparator.reverseOrder()))
                .limit(LIMIT)
                .toList();
    }
}
