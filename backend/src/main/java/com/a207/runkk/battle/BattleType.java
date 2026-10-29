package com.a207.runkk.battle;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 대결 종류(설계 6.1.6, 다형성 enum 6.4): 실시간 상대 여부, 비교표 항목, 이력 행 종류·필터 묶음, 승리 리워드, 진행 중 포기 처리, 시작 방식. 종류별
 * 차이는 상수 메소드에 모은다(v0.14 D-225).
 */
public enum BattleType {
    RANDOM {
        @Override
        public boolean hasLiveRival() {
            return true;
        }

        @Override
        public BattleType historyGroup() {
            return RANDOM;
        }

        @Override
        public BattleType filterGroup() {
            return RANDOM;
        }

        @Override
        public boolean rewardsWin() {
            return true;
        }

        @Override
        public boolean cancelsOnForfeit() {
            return false;
        }

        @Override
        public boolean startsWithCountdown() {
            return false;
        }
    },
    /** 고스트는 이력·화면에서 랜덤과 구분하지 않는다(Q-02). */
    GHOST {
        @Override
        public boolean hasLiveRival() {
            return false;
        }

        @Override
        public BattleType historyGroup() {
            return RANDOM;
        }

        @Override
        public BattleType filterGroup() {
            return RANDOM;
        }

        @Override
        public boolean rewardsWin() {
            return true;
        }

        @Override
        public boolean cancelsOnForfeit() {
            return false;
        }

        @Override
        public boolean startsWithCountdown() {
            return false;
        }
    },
    /** 친구 대결(FR-227~FR-230). 실시간 상대가 있고 승리 리워드가 없다. 이력 행은 FRIEND, 필터는 배틀런에 함께 보인다(D-225, D-230). */
    FRIEND {
        @Override
        public boolean hasLiveRival() {
            return true;
        }

        @Override
        public BattleType historyGroup() {
            return FRIEND;
        }

        @Override
        public BattleType filterGroup() {
            return RANDOM;
        }

        @Override
        public boolean rewardsWin() {
            return false;
        }

        @Override
        public boolean cancelsOnForfeit() {
            return false;
        }

        @Override
        public boolean startsWithCountdown() {
            return false;
        }
    },
    SELF {
        @Override
        public boolean hasLiveRival() {
            return false;
        }

        @Override
        public List<ComparisonItem> comparisonItems() {
            return SELF_ITEMS;
        }

        @Override
        public BattleType historyGroup() {
            return SELF;
        }

        @Override
        public BattleType filterGroup() {
            return SELF;
        }

        @Override
        public boolean rewardsWin() {
            return true;
        }

        @Override
        public boolean cancelsOnForfeit() {
            return true;
        }

        @Override
        public boolean startsWithCountdown() {
            return true;
        }
    },
    CELEBRITY {
        @Override
        public boolean hasLiveRival() {
            return false;
        }

        @Override
        public BattleType historyGroup() {
            return CELEBRITY;
        }

        @Override
        public BattleType filterGroup() {
            return CELEBRITY;
        }

        @Override
        public boolean rewardsWin() {
            return true;
        }

        @Override
        public boolean cancelsOnForfeit() {
            return true;
        }

        @Override
        public boolean startsWithCountdown() {
            return true;
        }
    };

    /** 셀프런 결과 비교표(기록 시간, 평균 페이스, D-229). */
    private static final List<ComparisonItem> SELF_ITEMS =
            List.of(ComparisonItem.RECORD_TIME, ComparisonItem.AVERAGE_PACE);

    /** 실시간으로 함께 달리는 상대가 있는가(RANDOM·FRIEND). 참이면 출발 때 실시간 세션을 연다. */
    public abstract boolean hasLiveRival();

    /** 기록 상대와 겨루는가(실시간 상대가 없다). */
    public boolean isAgainstRecord() {
        return !hasLiveRival();
    }

    /** 결과 비교표 줄 순서(FR-093, D-229). SELF만 두 줄이고 나머지는 비교표가 없다. */
    public List<ComparisonItem> comparisonItems() {
        return List.of();
    }

    /** 이력 행 종류(FR-101). 고스트는 랜덤으로 보이고 친구 대결은 FRIEND다. */
    public abstract BattleType historyGroup();

    /** 이력 필터 묶음(FR-101, D-230). 배틀런(RANDOM)은 랜덤·고스트·친구 대결을 함께 담는다. */
    public abstract BattleType filterGroup();

    /** 승리 리워드(BATTLE_WIN)를 주는가(FR-230, D-226). 친구 대결만 거짓. */
    public abstract boolean rewardsWin();

    /** 진행 중 포기를 대결 취소로 처리하는가(CR-27, D-228). 셀프런·셀럽런만 참. */
    public abstract boolean cancelsOnForfeit();

    /** true면 앱의 3초 카운트다운 뒤 시작 보고(markStarted), false면 성사 13초 뒤 자동 출발(D-17). */
    public abstract boolean startsWithCountdown();

    /** 승리 리워드를 주는 종류(일 단위 보정의 대결 거르기, D-226). */
    public static Set<BattleType> winRewarded() {
        EnumSet<BattleType> rewarded = EnumSet.noneOf(BattleType.class);
        Arrays.stream(values()).filter(BattleType::rewardsWin).forEach(rewarded::add);
        return Set.copyOf(rewarded);
    }
}
