package com.a207.runkk.runimal;

import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.TagCategory;
import com.a207.runkk.policy.RunimalRule;

/**
 * Runimal 4축(srs.md 3.6.1, 결정 Q-68). 상수마다 A쪽 비율을 기준과 비교한다(A-14 "축마다 threshold를 A쪽 비율로 바꿔 둔다"). 축 기준
 * 값은 운영 기준 RunimalRule의 구성요소를 직접 읽는다(1단계 리뷰 L13).
 */
public enum RunimalAxis {
    /** 동행: 크루런 비율이 기준(50%) 미만이면 솔로런(A). ratioOfA = 1 − 크루런 비율. */
    COMPANION(TagCategory.COMPANION, PersonalityTag.SOLO_RUN, PersonalityTag.SOCIAL_RUN) {
        @Override
        AxisLean lean(double ratioOfA, RunimalRule rule) {
            return 1 - ratioOfA < rule.companionRatio() ? AxisLean.A : AxisLean.B;
        }
    },
    /** 거리: 3km 미만 비율이 기준(50%) 이상이면 단거리(A). */
    DISTANCE(TagCategory.DISTANCE, PersonalityTag.SHORT_DISTANCE, PersonalityTag.LONG_DISTANCE) {
        @Override
        AxisLean lean(double ratioOfA, RunimalRule rule) {
            return ratioOfA >= rule.shortRatio() ? AxisLean.A : AxisLean.B;
        }
    },
    /** 코스: 같은 코스 비율이 기준(60%) 이상이면 단골길(A). */
    COURSE(TagCategory.COURSE, PersonalityTag.FAMILIAR_ROUTE, PersonalityTag.EXPLORER_ROUTE) {
        @Override
        AxisLean lean(double ratioOfA, RunimalRule rule) {
            return ratioOfA >= rule.sameCourseRatio() ? AxisLean.A : AxisLean.B;
        }
    },
    /** 습관: 평소 시각 ±2시간 안 비율이 기준(60%) 이상이면 루틴런(A). */
    HABIT(TagCategory.HABIT, PersonalityTag.ROUTINE_RUN, PersonalityTag.IMPROMPTU_RUN) {
        @Override
        AxisLean lean(double ratioOfA, RunimalRule rule) {
            return ratioOfA >= rule.habitRatio() ? AxisLean.A : AxisLean.B;
        }
    };

    private final TagCategory category;
    private final PersonalityTag sideA;
    private final PersonalityTag sideB;

    RunimalAxis(TagCategory category, PersonalityTag sideA, PersonalityTag sideB) {
        this.category = category;
        this.sideA = sideA;
        this.sideB = sideB;
    }

    abstract AxisLean lean(double ratioOfA, RunimalRule rule);

    /** 같은 이름의 성향 태그 분류(1~4분류). */
    public TagCategory category() {
        return category;
    }

    /** 성향 이름(예: 솔로런, 소셜런). */
    public String label(AxisLean lean) {
        return (lean == AxisLean.A ? sideA : sideB).label();
    }
}
