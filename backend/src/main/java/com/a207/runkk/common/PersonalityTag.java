package com.a207.runkk.common;

/**
 * 성향 태그 고정 목록 19개(srs.md 3.5.1, W-13). 운영 기준값이 아니라 고정 목록이다. 크루 태그는 S15P31A207-236에서
 * CrewTag(요일·시간대)로 바뀌어 지금은 Runimal 축 이름(RunimalAxis)만 이 목록을 쓴다.
 */
public enum PersonalityTag {
    SOLO_RUN(TagCategory.COMPANION, "솔로런"),
    SOCIAL_RUN(TagCategory.COMPANION, "소셜런"),
    SHORT_DISTANCE(TagCategory.DISTANCE, "단거리"),
    LONG_DISTANCE(TagCategory.DISTANCE, "장거리"),
    FAMILIAR_ROUTE(TagCategory.COURSE, "단골길"),
    EXPLORER_ROUTE(TagCategory.COURSE, "탐험길"),
    ROUTINE_RUN(TagCategory.HABIT, "루틴런"),
    IMPROMPTU_RUN(TagCategory.HABIT, "즉흥런"),
    DAWN(TagCategory.RUN_TIME, "새벽"),
    MORNING(TagCategory.RUN_TIME, "아침"),
    LUNCH(TagCategory.RUN_TIME, "점심"),
    EVENING(TagCategory.RUN_TIME, "저녁"),
    NIGHT(TagCategory.RUN_TIME, "밤"),
    PERSONAL_BEST(TagCategory.GOAL, "기록 단축"),
    RACE_PREP(TagCategory.GOAL, "대회 준비"),
    DIET(TagCategory.GOAL, "다이어트"),
    STRESS_RELIEF(TagCategory.GOAL, "스트레스 해소"),
    CONSISTENCY(TagCategory.GOAL, "꾸준히"),
    BEGINNER_WELCOME(TagCategory.CREW_MOOD, "초보 환영");

    private final TagCategory category;
    private final String label;

    PersonalityTag(TagCategory category, String label) {
        this.category = category;
        this.label = label;
    }

    public TagCategory category() {
        return category;
    }

    public String label() {
        return label;
    }
}
