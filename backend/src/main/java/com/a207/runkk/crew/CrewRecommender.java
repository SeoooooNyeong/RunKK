package com.a207.runkk.crew;

import com.a207.runkk.common.TagSet;
import java.util.List;

/**
 * 러너에게 맞는 크루를 고르는 계약(설계 6.1.7, 6.4). 기본 구현은 TagOverlapRecommender이고, 팀이 정하면 같은 인터페이스의
 * AiServerCrewRecommender로 바꿀 수 있다(부록 E E-03).
 */
public interface CrewRecommender {

    List<Recommendation> recommend(TagSet runnerTags, List<CrewCandidate> candidates);
}
