package com.a207.runkk.policy;

import com.a207.runkk.common.Distance;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 누적 거리를 랜드마크 표기로 바꾼다(설계 6.1.9, 6.2 A-18).
 *
 * @param landmarks 거리 오름차순
 */
public record LandmarkTable(List<Landmark> landmarks) {

    private static final Set<LandmarkStage> PERSONAL_STAGES =
            EnumSet.of(
                    LandmarkStage.DOMESTIC,
                    LandmarkStage.EAST_ASIA,
                    LandmarkStage.INTERCONTINENTAL);

    public LandmarkTable {
        landmarks = List.copyOf(landmarks);
    }

    /** 개인 누적 거리용. 1~3단계 랜드마크만 본다(FR-051). */
    public LandmarkProgress personalProgressOf(Distance total) {
        return progressAmong(
                landmarks.stream().filter(l -> PERSONAL_STAGES.contains(l.stage())).toList(),
                total);
    }

    /** 크루 누적 거리용. 1~4단계를 본다(FR-077). */
    public LandmarkProgress crewProgressOf(Distance total) {
        return progressAmong(landmarks, total);
    }

    private static LandmarkProgress progressAmong(List<Landmark> usable, Distance total) {
        Landmark passed = null;
        for (Landmark landmark : usable) {
            if (total.isAtLeast(landmark.distance())) {
                passed = landmark;
            }
        }
        if (passed == null) {
            Landmark first = usable.get(0);
            long percent = total.meters() * 100 / first.distance().meters();
            return new LandmarkProgress(
                    first.stage(), first.name(), "서울–" + first.name() + " 왕복까지 " + percent + "%");
        }
        return new LandmarkProgress(
                passed.stage(), passed.name(), passed.stage().label(passed, total));
    }
}
