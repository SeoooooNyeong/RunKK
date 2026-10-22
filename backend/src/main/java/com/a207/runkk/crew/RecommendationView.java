package com.a207.runkk.crew;

import java.util.List;

/**
 * 추천 크루 목록(자료사전 추천크루목록). 태그가 없는 러너면 빈 목록과 안내문이다(FR-119).
 *
 * @param crews 추천, 일치도 높은 순 최대 5개
 * @param guide 안내문, 없으면 null
 */
public record RecommendationView(List<Recommendation> crews, String guide) {

    public RecommendationView {
        crews = List.copyOf(crews);
    }
}
