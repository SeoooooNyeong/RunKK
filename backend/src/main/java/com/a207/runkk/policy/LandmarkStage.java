package com.a207.runkk.policy;

import com.a207.runkk.common.Distance;

/** 랜드마크 단계(설계 6.1.9). 상수가 자기 문구를 만든다(6.4). */
public enum LandmarkStage {
    DOMESTIC {
        @Override
        public String label(Landmark passed, Distance total) {
            long times = total.meters() / passed.distance().meters();
            return "서울에서 " + passed.name() + "까지 달렸어요! (왕복 " + times + "회)";
        }
    },
    EAST_ASIA {
        @Override
        public String label(Landmark passed, Distance total) {
            return oneWay(passed);
        }
    },
    INTERCONTINENTAL {
        @Override
        public String label(Landmark passed, Distance total) {
            if (passed.distance().meters() >= EARTH_METERS) {
                return earthLaps(total);
            }
            return oneWay(passed);
        }
    },
    CREW_TOTAL {
        @Override
        public String label(Landmark passed, Distance total) {
            long meters = total.meters();
            if (meters >= 2 * MOON_METERS) {
                return "달까지 다녀왔어요! (달 왕복 " + meters / (2 * MOON_METERS) + "회)";
            }
            if (meters >= MOON_METERS) {
                return "달에서 돌아오는 중 (" + (meters - MOON_METERS) * 100 / MOON_METERS + "%)";
            }
            if (meters >= MOON_APPROACH_METERS) {
                return "달나라를 향해 달리는 중! (달까지 " + meters * 100 / MOON_METERS + "%)";
            }
            return earthLaps(total);
        }
    };

    private static final long EARTH_METERS = 40_075_000L;
    private static final long MOON_APPROACH_METERS = 380_000_000L;
    private static final long MOON_METERS = 384_400_000L;

    /** 가장 최근에 넘은 랜드마크와 누적 거리로 화면 문구를 만든다. */
    public abstract String label(Landmark passed, Distance total);

    private static String oneWay(Landmark passed) {
        return "서울에서 " + passed.name() + "까지 달렸어요!";
    }

    private static String earthLaps(Distance total) {
        return "지구 " + total.meters() / EARTH_METERS + "바퀴째 도는 중!";
    }
}
