package com.a207.runkk.runimal;

import static com.a207.runkk.runimal.AxisLean.A;
import static com.a207.runkk.runimal.AxisLean.B;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/** Runimal 16유형(srs.md 3.6.1, 결정 Q-68). 아이콘은 동물 이름 텍스트다(FR-151). 축 순서는 동행·거리·코스·습관. */
public enum RunimalType {
    MORNING_SPARROW("아침 참새", "참새", "매일 같은 시간, 동네 한 바퀴 가볍게", A, A, A, A),
    WHEEL_HAMSTER("쳇바퀴 햄스터", "햄스터", "생각나면 익숙한 코스를 짧게 한 바퀴", A, A, A, B),
    ALLEY_SQUIRREL("골목 탐험 다람쥐", "다람쥐", "정해진 시간, 매번 다른 골목을 짧게 탐색", A, A, B, A),
    STROLLING_CAT("마실 나온 고양이", "고양이", "내킬 때 나와 처음 보는 골목을 슬쩍", A, A, B, B),
    COMMUTER_TURTLE("출근도장 거북이", "거북이", "같은 시간, 같은 길을 길고 꾸준하게", A, B, A, A),
    STEADY_CAMEL("묵묵한 낙타", "낙타", "생각나면 익숙한 길을 오래오래", A, B, A, B),
    SKY_EAGLE("하늘길 독수리", "독수리", "계획한 날, 먼 곳까지 새 길 개척", A, B, B, A),
    LONE_WOLF("고독한 늑대", "늑대", "아무도 모르는 길을 혼자 멀리", A, B, B, B),
    WALKING_PUPPY("산책 나온 강아지", "강아지", "매일 같은 시간, 친구들과 동네 한 바퀴", B, A, A, A),
    PLAYGROUND_MONKEY("놀이터 원숭이", "원숭이", "번개 모임에 짧고 신나게. 분위기 메이커", B, A, A, B),
    EXPLORER_OTTER("탐험대 수달", "수달", "정해진 날, 다 같이 새 동네 짧게 탐방", B, A, B, A),
    PICNIC_RABBIT("소풍 나온 토끼", "토끼", "즉흥적으로 모여 낯선 길을 가볍게", B, A, B, B),
    PILLAR_ELEPHANT("크루의 기둥 코끼리", "코끼리", "늘 그 시간, 그 길을 끝까지 함께", B, B, A, A),
    PENGUIN_SQUAD("펭귄 군단", "펭귄", "모이면 익숙한 길을 다 같이 길게 행진", B, B, A, B),
    LEADER_BIRD("V자 리더 철새", "철새", "먼 새 코스를 계획하고 크루를 이끄는 대장", B, B, B, A),
    WILD_HORSE("들판의 야생마", "야생마", "기분 내키면 친구들과 낯선 길을 멀리", B, B, B, B);

    private final String displayName;
    private final String icon;
    private final String description;
    private final Map<RunimalAxis, AxisLean> leans;

    RunimalType(
            String displayName,
            String icon,
            String description,
            AxisLean companion,
            AxisLean distance,
            AxisLean course,
            AxisLean habit) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
        Map<RunimalAxis, AxisLean> map = new EnumMap<>(RunimalAxis.class);
        map.put(RunimalAxis.COMPANION, companion);
        map.put(RunimalAxis.DISTANCE, distance);
        map.put(RunimalAxis.COURSE, course);
        map.put(RunimalAxis.HABIT, habit);
        this.leans = Map.copyOf(map);
    }

    /** 4축 성향 조합의 유형. 4축이 모두 있어야 한다. */
    public static RunimalType of(Map<RunimalAxis, AxisLean> leans) {
        if (!leans.keySet().containsAll(Arrays.asList(RunimalAxis.values()))) {
            throw new IllegalArgumentException("4축 성향이 모두 필요하다: " + leans.keySet());
        }
        Map<RunimalAxis, AxisLean> wanted = Map.copyOf(leans);
        return Arrays.stream(values())
                .filter(t -> t.leans.equals(wanted))
                .findFirst()
                .orElseThrow();
    }

    public String displayName() {
        return displayName;
    }

    public String icon() {
        return icon;
    }

    public String description() {
        return description;
    }

    public AxisLean lean(RunimalAxis axis) {
        return leans.get(axis);
    }
}
