package com.a207.runkk.battle;

import java.util.List;

/** 대결 종류(설계 6.1.6, 다형성 enum 6.4): 비교표 항목, 이력 분류, 시작 방식. */
public enum BattleType {
    RANDOM {
        @Override
        public List<ComparisonItem> comparisonItems() {
            return RACE_ITEMS;
        }

        @Override
        public BattleType historyGroup() {
            return RANDOM;
        }

        @Override
        public boolean startsWithCountdown() {
            return false;
        }
    },
    /** 고스트는 이력·화면에서 랜덤과 구분하지 않는다(Q-02). */
    GHOST {
        @Override
        public List<ComparisonItem> comparisonItems() {
            return RACE_ITEMS;
        }

        @Override
        public BattleType historyGroup() {
            return RANDOM;
        }

        @Override
        public boolean startsWithCountdown() {
            return false;
        }
    },
    SELF {
        @Override
        public List<ComparisonItem> comparisonItems() {
            return List.of(
                    ComparisonItem.RECORD_TIME,
                    ComparisonItem.AVERAGE_PACE,
                    ComparisonItem.FASTEST_KM,
                    ComparisonItem.MAX_LEAD);
        }

        @Override
        public BattleType historyGroup() {
            return SELF;
        }

        @Override
        public boolean startsWithCountdown() {
            return true;
        }
    },
    CELEBRITY {
        @Override
        public List<ComparisonItem> comparisonItems() {
            return List.of(
                    ComparisonItem.RECORD_TIME,
                    ComparisonItem.AVERAGE_PACE,
                    ComparisonItem.FASTEST_KM,
                    ComparisonItem.MAX_GAP);
        }

        @Override
        public BattleType historyGroup() {
            return CELEBRITY;
        }

        @Override
        public boolean startsWithCountdown() {
            return true;
        }
    };

    private static final List<ComparisonItem> RACE_ITEMS =
            List.of(
                    ComparisonItem.RECORD_TIME,
                    ComparisonItem.AVERAGE_PACE,
                    ComparisonItem.FASTEST_KM,
                    ComparisonItem.OVERTAKES);

    /** 결과 비교표 줄 순서(FR-093). */
    public abstract List<ComparisonItem> comparisonItems();

    /** 이력 필터 분류(FR-101). 고스트는 랜덤으로 묶는다. */
    public abstract BattleType historyGroup();

    /** true면 앱의 3초 카운트다운 뒤 시작 보고(markStarted), false면 성사 13초 뒤 자동 출발(D-17). */
    public abstract boolean startsWithCountdown();

    /** 기록 상대와 겨루는가. */
    public boolean isAgainstRecord() {
        return this != RANDOM;
    }
}
