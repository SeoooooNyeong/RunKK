package com.a207.runkk.battle;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DistanceUnit;
import com.a207.runkk.common.Pace;
import com.a207.runkk.running.RunComparison;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/** 대결 비교표 항목(설계 6.1.6, 다형성 enum 6.4). 상수마다 본인 값·상대 값을 문구로 만든다. 상대 값이 없으면 "공개 안 됨"(FR-093). */
public enum ComparisonItem {
    RECORD_TIME("기록") {
        @Override
        public String mine(RunComparison r, BattleParticipant p) {
            return time(Optional.ofNullable(r.elapsed()));
        }

        @Override
        public String theirs(ComparisonValues v) {
            return v.elapsedValue().map(ComparisonItem::formatTime).orElse(NOT_DISCLOSED);
        }
    },
    AVERAGE_PACE("평균 페이스") {
        @Override
        public String mine(RunComparison r, BattleParticipant p) {
            return pace(Optional.ofNullable(r.averagePace()));
        }

        @Override
        public String theirs(ComparisonValues v) {
            return v.averagePaceValue().map(ComparisonItem::formatPace).orElse(NOT_DISCLOSED);
        }
    },
    FASTEST_KM("가장 빠른 1km") {
        @Override
        public String mine(RunComparison r, BattleParticipant p) {
            return pace(Optional.ofNullable(r.fastestKm()));
        }

        @Override
        public String theirs(ComparisonValues v) {
            return v.fastestKmValue().map(ComparisonItem::formatPace).orElse(NOT_DISCLOSED);
        }
    },
    OVERTAKES("추월 횟수") {
        @Override
        public String mine(RunComparison r, BattleParticipant p) {
            return p.getOvertakes() + COUNT_SUFFIX;
        }

        @Override
        public String theirs(ComparisonValues v) {
            return v.overtakesValue().map(n -> n + COUNT_SUFFIX).orElse(NOT_DISCLOSED);
        }
    },
    MAX_LEAD("가장 앞섰을 때 거리 차") {
        @Override
        public String mine(RunComparison r, BattleParticipant p) {
            return meters(p.maxLead());
        }

        @Override
        public String theirs(ComparisonValues v) {
            return meters(v.maxLeadValue());
        }
    },
    MAX_GAP("최대 거리 차") {
        @Override
        public String mine(RunComparison r, BattleParticipant p) {
            return meters(p.maxLead());
        }

        @Override
        public String theirs(ComparisonValues v) {
            return meters(v.maxLeadValue());
        }
    };

    /** 상대 값이 공개되지 않았을 때(FR-093). */
    public static final String NOT_DISCLOSED = "공개 안 됨";

    private static final String NO_VALUE = "-";
    private static final String COUNT_SUFFIX = "회";
    private static final String METER_SUFFIX = "m";
    private static final long SECONDS_PER_HOUR = 3_600;
    private static final long SECONDS_PER_MINUTE = 60;

    private final String label;

    ComparisonItem(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public abstract String mine(RunComparison r, BattleParticipant p);

    public abstract String theirs(ComparisonValues v);

    private static String time(Optional<Duration> value) {
        return value.map(ComparisonItem::formatTime).orElse(NO_VALUE);
    }

    private static String pace(Optional<Pace> value) {
        return value.map(ComparisonItem::formatPace).orElse(NO_VALUE);
    }

    private static String meters(Optional<Distance> value) {
        return value.map(d -> d.meters() + METER_SUFFIX).orElse(NO_VALUE);
    }

    private static String formatPace(Pace value) {
        return value.format(DistanceUnit.KM);
    }

    /** h:mm:ss 또는 mm:ss. */
    static String formatTime(Duration d) {
        long seconds = d.toSeconds();
        long hours = seconds / SECONDS_PER_HOUR;
        long minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE;
        long rest = seconds % SECONDS_PER_MINUTE;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, rest);
    }
}
