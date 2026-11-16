package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.member.ProfileVisibility;
import com.a207.runkk.running.RunQueryService;
import java.time.Duration;
import java.time.LocalDateTime;

/** battle 시험용 값. */
public final class BattleFixtures {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 19, 0);
    public static final long ALICE = 1L;
    public static final long BOB = 2L;

    private BattleFixtures() {}

    /** 표시명 "러너{id}", 공개 프로필. */
    public static MemberSummary summary(long memberId, Tier tier) {
        return new MemberSummary(
                memberId, DisplayName.of("러너" + memberId), tier, ProfileVisibility.PUBLIC);
    }

    public static MatchTicket assigned(long memberId, StandardDistance d) {
        MatchTicket ticket = MatchTicket.open(memberId, Tier.GOLD, d, NOW);
        ticket.assign();
        return ticket;
    }

    /** 랜덤 5km 대결, NOW + 13초에 시작했다. */
    public static Battle startedRandom() {
        Battle battle =
                Battle.random(
                        assigned(ALICE, StandardDistance.KM5),
                        assigned(BOB, StandardDistance.KM5),
                        NOW);
        battle.scheduleStart(NOW.plusSeconds(13));
        battle.start(NOW.plusSeconds(13));
        return battle;
    }

    public static LocalDateTime startOf(Battle battle) {
        return battle.getStartAt().orElseThrow();
    }

    /** 기록 상대 5km, 도착 경과 시간 arrival. */
    public static RecordBattleSpec spec(BattleType type, Duration arrival) {
        Distance d = StandardDistance.KM5.distance();
        OpponentRef ref =
                new OpponentRef(
                        OpponentKind.VIRTUAL, "77", DisplayName.of("가상러너"), Tier.GOLD, arrival);
        return new RecordBattleSpec(
                type,
                ALICE,
                d,
                ref,
                OpponentTrack.uniform(d, arrival),
                ComparisonValues.undisclosed());
    }

    /** 모습을 주지 않는 OpponentLooks(모습과 무관한 시험용). 지금 모습도 기록의 모습도 비어 있다. */
    public static OpponentLooks noLooks() {
        return new OpponentLooks(
                memberId -> java.util.Optional.empty(),
                org.mockito.Mockito.mock(RunQueryService.class));
    }
}
