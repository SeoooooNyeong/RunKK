package com.a207.runkk.runimal;

import static com.a207.runkk.runimal.AxisLean.A;
import static com.a207.runkk.runimal.AxisLean.B;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
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

    /** 유형 상세의 특징 3줄(srs 3.6.1 특징 열, FR-226, v0.14 D-246). */
    public List<String> traits() {
        return switch (this) {
            case MORNING_SPARROW ->
                    List.of(
                            "매일 비슷한 시간에 집 가까이에서 시작해요",
                            "3km가 안 되는 가벼운 거리를 달려요",
                            "익숙한 동네 코스를 혼자 반복해요");
            case WHEEL_HAMSTER ->
                    List.of("생각날 때 불쑥 나가서 달려요", "짧은 거리를 가볍게 마무리해요", "늘 달리던 코스를 혼자 한 바퀴 돌아요");
            case ALLEY_SQUIRREL ->
                    List.of("정해 둔 시간에 맞춰 혼자 나서요", "짧은 거리 안에서 부지런히 움직여요", "갈 때마다 다른 골목을 골라 달려요");
            case STROLLING_CAT ->
                    List.of("내킬 때만 불쑥 혼자 나와요", "짧은 거리를 슬쩍 달리고 돌아와요", "처음 가 보는 길을 기웃거려요");
            case COMMUTER_TURTLE ->
                    List.of(
                            "혼자 정해 둔 코스를 반복해서 달려요",
                            "5km 이상 긴 거리를 편하게 소화해요",
                            "비슷한 요일·시간대에 꾸준히 기록을 남겨요");
            case STEADY_CAMEL ->
                    List.of("시간을 정하지 않고 생각나면 혼자 나가요", "5km 이상 긴 거리를 오래 달려요", "익숙한 길을 묵묵히 반복해요");
            case SKY_EAGLE ->
                    List.of("계획한 날과 시간에 맞춰 혼자 출발해요", "5km 이상 먼 거리를 소화해요", "안 가 본 길로 코스를 넓혀 가요");
            case LONE_WOLF ->
                    List.of("정해진 때 없이 마음 내킬 때 혼자 나가요", "멀리까지 오래 달려요", "아무도 모르는 새 길을 찾아다녀요");
            case WALKING_PUPPY ->
                    List.of("매일 비슷한 시간에 친구와 만나 달려요", "3km가 안 되는 가벼운 거리를 즐겨요", "같이 달리던 동네 코스를 반복해요");
            case PLAYGROUND_MONKEY ->
                    List.of("번개처럼 모여서 즉흥적으로 달려요", "짧은 거리를 신나게 달려요", "함께 달리던 익숙한 코스를 자주 써요");
            case EXPLORER_OTTER ->
                    List.of("정해진 날 친구와 함께 모여요", "짧은 거리를 다 같이 달려요", "함께 새로운 동네를 찾아다녀요");
            case PICNIC_RABBIT -> List.of("갑자기 모여서 함께 달려요", "짧고 가벼운 거리를 즐겨요", "낯선 길을 같이 둘러봐요");
            case PILLAR_ELEPHANT ->
                    List.of("늘 같은 시간에 사람들과 함께 달려요", "5km 이상 긴 거리를 끝까지 함께해요", "익숙한 코스를 든든하게 지켜요");
            case PENGUIN_SQUAD ->
                    List.of("모이면 바로 함께 달려요", "5km 이상 긴 거리를 다 같이 가요", "익숙한 코스를 줄지어 반복해요");
            case LEADER_BIRD ->
                    List.of("일정을 미리 정해 함께 출발해요", "5km 이상 먼 거리를 이끌어요", "새 코스를 직접 찾아 앞장서요");
            case WILD_HORSE ->
                    List.of("기분 내키면 친구들과 바로 나가요", "5km 이상 먼 거리를 신나게 달려요", "낯선 길로 거침없이 달려 나가요");
        };
    }
}
