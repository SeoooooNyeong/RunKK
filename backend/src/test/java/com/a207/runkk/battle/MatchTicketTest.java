package com.a207.runkk.battle;

import static com.a207.runkk.battle.BattleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.policy.MatchCondition;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MatchTicket 상태도 2.4.1과 srs.md 6.3 4.2 후보 판정 표(14장 단위). */
@DisplayName("MatchTicket 대기 표")
class MatchTicketTest {

    private static final MatchCondition SAME_TIER = new MatchCondition(0, Duration.ofSeconds(15));

    private static MatchTicket ticket(long memberId, Tier tier, StandardDistance d) {
        return MatchTicket.open(memberId, tier, d, NOW);
    }

    @Test
    @DisplayName("open: 언랭·랜덤 대결이 아닌 거리는 거부")
    void open_rejectsUnrankedAndNonRandomDistance() {
        assertThatThrownBy(() -> ticket(1, Tier.UNRANKED, StandardDistance.KM5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ticket(1, Tier.GOLD, StandardDistance.HALF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ticket(1, Tier.GOLD, StandardDistance.KM1).getStatus())
                .isEqualTo(MatchStatus.WAITING);
    }

    @Test
    @DisplayName("후보 판정 표 4규칙: 같은 회원·거리 다름·티어 다름은 후보 아님, 모두 맞으면 후보")
    void canPairWith_decisionTable() {
        MatchTicket a = ticket(1, Tier.GOLD, StandardDistance.KM5);
        assertThat(a.canPairWith(ticket(1, Tier.GOLD, StandardDistance.KM5), SAME_TIER)).isFalse();
        assertThat(a.canPairWith(ticket(2, Tier.GOLD, StandardDistance.KM3), SAME_TIER)).isFalse();
        assertThat(a.canPairWith(ticket(2, Tier.SILVER, StandardDistance.KM5), SAME_TIER))
                .isFalse();
        assertThat(a.canPairWith(ticket(2, Tier.GOLD, StandardDistance.KM5), SAME_TIER)).isTrue();
        MatchCondition wide = new MatchCondition(1, Duration.ofSeconds(15));
        assertThat(a.canPairWith(ticket(2, Tier.SILVER, StandardDistance.KM5), wide)).isTrue();
    }

    @Test
    @DisplayName("배정된 표는 후보가 아니고 다시 배정할 수 없다")
    void assign_onlyOnce() {
        MatchTicket a = ticket(1, Tier.GOLD, StandardDistance.KM5);
        MatchTicket b = ticket(2, Tier.GOLD, StandardDistance.KM5);
        b.assign();
        assertThat(b.getStatus()).isEqualTo(MatchStatus.ASSIGNED);
        assertThat(a.canPairWith(b, SAME_TIER)).isFalse();
        assertThatThrownBy(b::assign).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("isExpired: 15초를 넘어야 만료(경곗값 15초는 아직 대기)")
    void isExpired_boundary() {
        MatchTicket a = ticket(1, Tier.GOLD, StandardDistance.KM5);
        Duration ghostAfter = Duration.ofSeconds(15);
        assertThat(a.isExpired(NOW.plusSeconds(15), ghostAfter)).isFalse();
        assertThat(a.isExpired(NOW.plusSeconds(15).plusNanos(1_000_000), ghostAfter)).isTrue();
    }
}
