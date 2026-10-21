package com.a207.runkk.crew;

import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import com.a207.runkk.policy.CrewRunRule;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/** crew 시험용 입력 값. */
public final class CrewFixtures {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 9, 0);
    public static final long LEADER = 100;
    public static final CrewRunRule RULE =
            new CrewRunRule(Duration.ofMinutes(30), Duration.ofMinutes(10), 2, 15);

    private CrewFixtures() {}

    public static CrewProfile profile(String name) {
        return profile(name, CrewVisibility.PUBLIC, JoinPolicy.FREE);
    }

    public static CrewProfile profile(String name, CrewVisibility visibility, JoinPolicy policy) {
        return new CrewProfile(
                CrewName.of(name),
                "함께 달려요",
                TagSet.of(Set.of(PersonalityTag.SOCIAL_RUN, PersonalityTag.MORNING), TagOwner.CREW),
                visibility,
                policy,
                null);
    }

    public static InviteCode code(String code) {
        return InviteCode.issue(code, NOW);
    }

    /** 크루장 LEADER와 크루원 members로 이뤄진 자유 가입 공개 크루. */
    public static Crew crew(String name, long... members) {
        Crew crew = Crew.create(LEADER, profile(name), code("ABC123"), NOW);
        for (long m : members) {
            crew.admit(m, NOW);
        }
        return crew;
    }

    /** scheduledAt에 5km를 달리는 모집글(작성자 LEADER, 투표 마감 없음). */
    public static CrewRunPost crewRun(long crewId, LocalDateTime scheduledAt) {
        return new CrewRunPost(
                crewId,
                LEADER,
                new CrewRunDraft("토요일 아침 같이 달려요", scheduledAt, StandardDistance.KM5, null),
                NOW);
    }
}
