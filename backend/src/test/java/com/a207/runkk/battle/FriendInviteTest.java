package com.a207.runkk.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * FriendInvite 상태 전이와 만료(설계 2.4.11, D-218·D-221, test.md 12.2.1 TC-9201~TC-9230, 화이트박스
 * TC-9507~TC-9512, 경곗값 TC-9531~TC-9538). 생성 12:00:00, 만료 12:05:00 초대 하나를 기준으로 한다.
 */
@DisplayName("FriendInvite 상태 전이와 만료")
class FriendInviteTest {

    static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 29, 12, 0);
    static final long INVITER = 7L;
    static final long JOINER = 8L;

    static FriendInvite open() {
        return FriendInvite.open(INVITER, StandardDistance.KM5, CREATED);
    }

    private static LocalDateTime at(int minutes, int seconds) {
        return CREATED.plusMinutes(minutes).plusSeconds(seconds);
    }

    private static void assertReason(Runnable call, ReasonCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(code);
    }

    @Test
    @DisplayName("TC-9201 open: PENDING, 22자 base64url 토큰, 만료 = 생성 + 5분, 참가자·대결·닫힌 시각 없음")
    void tc9201_open() {
        FriendInvite invite = open();
        assertThat(invite.getStatus()).isEqualTo(FriendInviteStatus.PENDING);
        assertThat(invite.getToken()).hasSize(22).matches("^[A-Za-z0-9_-]{22}$");
        assertThat(invite.getExpiresAt()).isEqualTo(at(5, 0));
        assertThat(invite.getJoinerId()).isEmpty();
        assertThat(invite.getBattleId()).isEmpty();
        assertThat(invite.getClosedAt()).isEmpty();
        assertThat(invite.getVersion()).isZero();
        assertThat(invite.getInviterId()).isEqualTo(INVITER);
    }

    @ParameterizedTest(name = "TC-9202 {0}")
    @EnumSource(
            value = StandardDistance.class,
            names = {"KM1", "KM3", "KM5", "KM10"})
    @DisplayName("TC-9202 랜덤 대결 거리 네 개는 초대를 만든다")
    void tc9202_validDistances(StandardDistance d) {
        assertThat(FriendInvite.open(INVITER, d, CREATED).getDistance()).isEqualTo(d);
    }

    @ParameterizedTest(name = "TC-9203 {0}")
    @EnumSource(
            value = StandardDistance.class,
            names = {"HALF", "FULL"})
    @DisplayName("TC-9203 하프·풀은 IllegalArgumentException")
    void tc9203_invalidDistances(StandardDistance d) {
        assertThatThrownBy(() -> FriendInvite.open(INVITER, d, CREATED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FriendInvite.open(INVITER, null, CREATED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-9204 토큰 1,000개가 모두 다르고 22자, 패딩·공백 없음")
    void tc9204_tokensAreUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String token = open().getToken();
            assertThat(token).hasSize(22).doesNotContain("=").doesNotContain(" ");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(1_000);
    }

    @ParameterizedTest(name = "TC-9205~9209 {0}분 {1}초 {2}나노 → {3}")
    @CsvSource({
        "4,59,0,PENDING",
        "4,59,999000000,PENDING",
        "5,0,0,EXPIRED",
        "5,1,0,EXPIRED",
        "0,-1,0,PENDING"
    })
    @DisplayName("TC-9205~TC-9209 statusAt 만료 경계(정각은 만료, 시계 역행은 만료 아님), 저장 상태는 그대로")
    void tc9205_statusAtBoundaries(int min, int sec, int nanos, FriendInviteStatus expected) {
        FriendInvite invite = open();
        assertThat(invite.statusAt(at(min, sec).plusNanos(nanos))).isEqualTo(expected);
        assertThat(invite.getStatus()).isEqualTo(FriendInviteStatus.PENDING);
    }

    @Test
    @DisplayName("TC-9210 accept 4:59 → ACCEPTED, 참가자 8, 닫힌 시각")
    void tc9210_acceptBeforeExpiry() {
        FriendInvite invite = open();
        invite.accept(JOINER, at(4, 59));
        assertThat(invite.getStatus()).isEqualTo(FriendInviteStatus.ACCEPTED);
        assertThat(invite.getJoinerId()).contains(JOINER);
        assertThat(invite.getClosedAt()).contains(at(4, 59));
    }

    @ParameterizedTest(name = "TC-9211/9212 {0}초 뒤")
    @CsvSource({"0", "1"})
    @DisplayName("TC-9211·TC-9212 accept 만료 정각·1초 뒤 → INVITE_EXPIRED, 상태 그대로")
    void tc9211_acceptExpired(int seconds) {
        FriendInvite invite = open();
        assertReason(() -> invite.accept(JOINER, at(5, seconds)), ReasonCode.INVITE_EXPIRED);
        assertThat(invite.getStatus()).isEqualTo(FriendInviteStatus.PENDING);
        assertThat(invite.getJoinerId()).isEmpty();
    }

    @Test
    @DisplayName("TC-9213 본인 초대 수락 → INVITE_OWN")
    void tc9213_acceptOwn() {
        FriendInvite invite = open();
        assertReason(() -> invite.accept(INVITER, at(1, 0)), ReasonCode.INVITE_OWN);
        assertThat(invite.getStatus()).isEqualTo(FriendInviteStatus.PENDING);
    }

    @Test
    @DisplayName(
            "TC-9214~TC-9216 끝 상태에서 accept: ACCEPTED·DECLINED·CANCELLED·EXPIRED 각 거부 사유, 상태 그대로")
    void tc9214_acceptFinalStates() {
        FriendInvite accepted = open();
        accepted.accept(9L, at(0, 30));
        assertReason(() -> accepted.accept(JOINER, at(1, 0)), ReasonCode.INVITE_ALREADY_JOINED);
        assertThat(accepted.getJoinerId()).contains(9L);

        FriendInvite declined = open();
        declined.decline(9L, at(0, 30));
        assertReason(() -> declined.accept(JOINER, at(1, 0)), ReasonCode.INVITE_CANCELLED);
        assertThat(declined.getStatus()).isEqualTo(FriendInviteStatus.DECLINED);

        FriendInvite cancelled = open();
        cancelled.cancel(at(0, 30));
        assertReason(() -> cancelled.accept(JOINER, at(1, 0)), ReasonCode.INVITE_CANCELLED);

        FriendInvite expired = open();
        expired.expireIfDue(at(5, 0));
        assertReason(() -> expired.accept(JOINER, at(1, 0)), ReasonCode.INVITE_EXPIRED);
        assertThat(expired.getStatus()).isEqualTo(FriendInviteStatus.EXPIRED);
    }

    @Test
    @DisplayName("TC-9217 본인이고 기한도 지남 → INVITE_OWN(본인 검사가 먼저)")
    void tc9217_ownBeforeExpiry() {
        assertReason(() -> open().accept(INVITER, at(6, 0)), ReasonCode.INVITE_OWN);
    }

    @Test
    @DisplayName(
            "TC-9218~TC-9220 decline: 4:59 DECLINED, 정각 INVITE_EXPIRED, 본인 INVITE_OWN, ACCEPTED"
                    + " INVITE_ALREADY_JOINED")
    void tc9218_decline() {
        FriendInvite invite = open();
        invite.decline(JOINER, at(4, 59));
        assertThat(invite.getStatus()).isEqualTo(FriendInviteStatus.DECLINED);
        assertThat(invite.getClosedAt()).contains(at(4, 59));

        assertReason(() -> open().decline(JOINER, at(5, 0)), ReasonCode.INVITE_EXPIRED);
        assertReason(() -> open().decline(INVITER, at(1, 0)), ReasonCode.INVITE_OWN);
        FriendInvite accepted = open();
        accepted.accept(9L, at(0, 30));
        assertReason(() -> accepted.decline(JOINER, at(1, 0)), ReasonCode.INVITE_ALREADY_JOINED);
        assertThat(accepted.getStatus()).isEqualTo(FriendInviteStatus.ACCEPTED);
    }

    @Test
    @DisplayName(
            "TC-9221~TC-9224 cancel: PENDING(기한 지나도) 참, 끝 상태 거짓(멱등), ACCEPTED"
                    + " INVITE_ALREADY_JOINED")
    void tc9221_cancel() {
        FriendInvite pending = open();
        assertThat(pending.cancel(at(3, 0))).isTrue();
        assertThat(pending.getStatus()).isEqualTo(FriendInviteStatus.CANCELLED);

        FriendInvite late = open();
        assertThat(late.cancel(at(6, 0))).isTrue();
        assertThat(late.getStatus()).isEqualTo(FriendInviteStatus.CANCELLED);

        assertThat(pending.cancel(at(4, 0))).isFalse();
        FriendInvite expired = open();
        expired.expireIfDue(at(5, 0));
        assertThat(expired.cancel(at(6, 0))).isFalse();
        assertThat(expired.getStatus()).isEqualTo(FriendInviteStatus.EXPIRED);
        FriendInvite declined = open();
        declined.decline(JOINER, at(1, 0));
        assertThat(declined.cancel(at(2, 0))).isFalse();

        FriendInvite accepted = open();
        accepted.accept(JOINER, at(1, 0));
        assertReason(() -> accepted.cancel(at(2, 0)), ReasonCode.INVITE_ALREADY_JOINED);
        assertThat(accepted.getStatus()).isEqualTo(FriendInviteStatus.ACCEPTED);
    }

    @Test
    @DisplayName("TC-9225~TC-9227 expireIfDue: 4:59 거짓, 정각·1초 뒤 참(EXPIRED 저장), 끝 상태는 거짓")
    void tc9225_expireIfDue() {
        FriendInvite before = open();
        assertThat(before.expireIfDue(at(4, 59))).isFalse();
        assertThat(before.getStatus()).isEqualTo(FriendInviteStatus.PENDING);

        FriendInvite exact = open();
        assertThat(exact.expireIfDue(at(5, 0))).isTrue();
        assertThat(exact.getStatus()).isEqualTo(FriendInviteStatus.EXPIRED);
        FriendInvite after = open();
        assertThat(after.expireIfDue(at(5, 1))).isTrue();

        FriendInvite accepted = open();
        accepted.accept(JOINER, at(1, 0));
        FriendInvite declined = open();
        declined.decline(JOINER, at(1, 0));
        FriendInvite cancelled = open();
        cancelled.cancel(at(1, 0));
        for (FriendInvite done : new FriendInvite[] {accepted, declined, cancelled}) {
            FriendInviteStatus status = done.getStatus();
            assertThat(done.expireIfDue(at(30, 0))).isFalse();
            assertThat(done.getStatus()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName(
            "TC-9228·TC-9229 linkBattle: ACCEPTED만 잇고 PENDING·CANCELLED는 IllegalStateException")
    void tc9228_linkBattle() {
        FriendInvite accepted = open();
        accepted.accept(JOINER, at(1, 0));
        accepted.linkBattle(55L);
        assertThat(accepted.getBattleId()).contains(55L);
        assertThat(accepted.getStatus()).isEqualTo(FriendInviteStatus.ACCEPTED);

        assertThatThrownBy(() -> open().linkBattle(55L)).isInstanceOf(IllegalStateException.class);
        FriendInvite cancelled = open();
        cancelled.cancel(at(1, 0));
        assertThatThrownBy(() -> cancelled.linkBattle(55L))
                .isInstanceOf(IllegalStateException.class);
        assertThat(cancelled.getBattleId()).isEmpty();
    }

    @Test
    @DisplayName("TC-9230 FriendInviteStatus.refusal·isFinal")
    void tc9230_statusConstants() {
        assertThat(FriendInviteStatus.PENDING.refusal()).isEmpty();
        assertThat(FriendInviteStatus.ACCEPTED.refusal())
                .contains(ReasonCode.INVITE_ALREADY_JOINED);
        assertThat(FriendInviteStatus.DECLINED.refusal()).contains(ReasonCode.INVITE_CANCELLED);
        assertThat(FriendInviteStatus.CANCELLED.refusal()).contains(ReasonCode.INVITE_CANCELLED);
        assertThat(FriendInviteStatus.EXPIRED.refusal()).contains(ReasonCode.INVITE_EXPIRED);
        assertThat(FriendInviteStatus.values())
                .filteredOn(FriendInviteStatus::isFinal)
                .containsExactly(
                        FriendInviteStatus.ACCEPTED,
                        FriendInviteStatus.DECLINED,
                        FriendInviteStatus.CANCELLED,
                        FriendInviteStatus.EXPIRED);
    }

    @Test
    @DisplayName("TC-9507~TC-9512 refusalFor·statusAt 기초 경로")
    void tc9507_refusalAndStatusPaths() {
        FriendInvite invite = open();
        assertThat(invite.refusalFor(INVITER, at(1, 0))).contains(ReasonCode.INVITE_OWN);
        assertThat(invite.refusalFor(JOINER, at(5, 0))).contains(ReasonCode.INVITE_EXPIRED);
        assertThat(invite.refusalFor(JOINER, at(1, 0))).isEqualTo(Optional.empty());

        FriendInvite accepted = open();
        accepted.accept(JOINER, at(1, 0));
        assertThat(accepted.statusAt(at(10, 0))).isEqualTo(FriendInviteStatus.ACCEPTED);
        assertThat(open().statusAt(at(4, 59))).isEqualTo(FriendInviteStatus.PENDING);
        assertThat(open().statusAt(at(5, 0))).isEqualTo(FriendInviteStatus.EXPIRED);
    }

    @ParameterizedTest(name = "TC-9531~9536 경과 {0}초 → 수락 {1}")
    @CsvSource({"-1,true", "0,true", "1,true", "299,true", "300,false", "301,false"})
    @DisplayName("TC-9531~TC-9536 수락 시각 경곗값(−1/0/1/299/300/301초)")
    void tc9531_acceptBoundary(int elapsedSeconds, boolean accepted) {
        FriendInvite invite = open();
        LocalDateTime now = CREATED.plusSeconds(elapsedSeconds);
        if (accepted) {
            invite.accept(JOINER, now);
            assertThat(invite.getStatus()).isEqualTo(FriendInviteStatus.ACCEPTED);
        } else {
            assertReason(() -> invite.accept(JOINER, now), ReasonCode.INVITE_EXPIRED);
        }
    }

    @Test
    @DisplayName("TC-9537·TC-9538 만료 시각은 늘지 않고, 4:50에 본 뒤 5:10에 참가하면 INVITE_EXPIRED")
    void tc9537_expiryDoesNotExtend() {
        FriendInvite invite = open();
        assertThat(invite.refusalFor(JOINER, at(4, 50))).isEmpty();
        assertThat(invite.getExpiresAt()).isEqualTo(at(5, 0));
        assertReason(() -> invite.accept(JOINER, at(5, 10)), ReasonCode.INVITE_EXPIRED);
    }

    /** 시험용: 저장된 상태를 바꾼다(저장소가 만료를 반영했을 때). */
    static void forceStatus(FriendInvite invite, FriendInviteStatus status) {
        ReflectionTestUtils.setField(invite, "status", status);
    }
}
