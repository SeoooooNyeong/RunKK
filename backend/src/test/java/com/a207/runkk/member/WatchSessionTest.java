package com.a207.runkk.member;

import static com.a207.runkk.member.MemberFixtures.NOW;
import static com.a207.runkk.member.MemberFixtures.requiredConsents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 워치용 토큰 쌍 발급(D-372, FR-240, SQ-43): AuthService.issueWatchSession. 폰 토큰을 무효로 만들지 않는다. 워치용 갱신 토큰은
 * 회전해도 워치용이고, 워치연동 끄기·로그아웃·탈퇴 때 그 회원의 워치용 갱신 토큰만 모두 폐기한다(2026-11-01 사용자 결정 W25, 코드 리뷰 12차 U12-6).
 */
@DisplayName("AuthService.issueWatchSession: 워치용 토큰 쌍")
class WatchSessionTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private final InMemoryMemberRepository repository = new InMemoryMemberRepository();
    private final FakeTokenIssuer tokens = new FakeTokenIssuer();
    private final AuthService auth =
            new AuthService(
                    List.of(),
                    repository,
                    tokens,
                    clock,
                    TransactionOperations.withoutTransaction());

    private Member social(String nickname) {
        return repository.save(
                Member.signUp(
                        new SocialAccount(ProviderType.KAKAO, "k-" + nickname),
                        new SignUpForm(null, requiredConsents()),
                        Nickname.of(nickname),
                        NOW.minusDays(3)));
    }

    @Test
    @DisplayName("회원에게 새 토큰 쌍을 주고, 폰의 갱신 토큰은 그대로 쓸 수 있다")
    void issuesNewPair_phoneTokensStayValid() {
        Member m = social("워치곰");
        TokenPair phone = tokens.issue(m.getId());

        TokenPair watch = auth.issueWatchSession(m.getId());

        assertThat(watch.refreshToken()).isNotEqualTo(phone.refreshToken());
        assertThat(watch.accessToken()).isEqualTo("access-" + m.getId());
        assertThat(tokens.isRevoked(phone.refreshToken())).isFalse();
        assertThat(auth.refresh(phone.refreshToken()).accessToken())
                .isEqualTo("access-" + m.getId());
        assertThat(auth.refresh(watch.refreshToken()).accessToken())
                .isEqualTo("access-" + m.getId());
    }

    @Test
    @DisplayName("게스트도 받는다")
    void guest_isIssued() {
        Member guest =
                repository.save(Member.guest(Nickname.of("러너0042"), requiredConsents(), NOW));
        assertThat(guest.getAccountType()).isEqualTo(AccountType.GUEST);

        assertThat(auth.issueWatchSession(guest.getId()).accessToken())
                .isEqualTo("access-" + guest.getId());
    }

    @Test
    @DisplayName("탈퇴했거나 없는 회원은 AuthFailureException, 토큰을 발급하지 않는다")
    void withdrawnOrMissing_isRejected() {
        Member gone = social("탈퇴곰");
        gone.withdraw(NOW);
        int issued = tokens.issuedCount();

        assertThatThrownBy(() -> auth.issueWatchSession(gone.getId()))
                .isInstanceOf(AuthFailureException.class);
        assertThatThrownBy(() -> auth.issueWatchSession(999_999L))
                .isInstanceOf(AuthFailureException.class);
        assertThat(tokens.issuedCount()).isEqualTo(issued);
    }

    @Test
    @DisplayName("워치용 갱신 토큰은 갱신해도 워치용 토큰 쌍을 받는다(폰 토큰은 폰용)")
    void watchRefresh_staysWatch() {
        Member m = social("회전곰");
        TokenPair phone = tokens.issue(m.getId());
        TokenPair watch = auth.issueWatchSession(m.getId());

        assertThat(tokens.isWatch(auth.refresh(watch.refreshToken()).refreshToken())).isTrue();
        assertThat(tokens.isWatch(auth.refresh(phone.refreshToken()).refreshToken())).isFalse();
    }

    @Test
    @DisplayName("revokeWatchSessions: 회전한 것까지 그 회원의 워치용 갱신 토큰만 모두 폐기하고 폰과 다른 회원은 그대로다")
    void revokeWatchSessions_revokesOnlyThatMembersWatchTokens() {
        Member m = social("끄기곰");
        Member other = social("남곰");
        TokenPair phone = tokens.issue(m.getId());
        TokenPair rotated = auth.refresh(auth.issueWatchSession(m.getId()).refreshToken());
        TokenPair second = auth.issueWatchSession(m.getId());
        TokenPair othersWatch = auth.issueWatchSession(other.getId());

        auth.revokeWatchSessions(m.getId());

        assertThatThrownBy(() -> auth.refresh(rotated.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
        assertThatThrownBy(() -> auth.refresh(second.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
        assertThat(auth.refresh(phone.refreshToken()).accessToken())
                .isEqualTo("access-" + m.getId());
        assertThat(auth.refresh(othersWatch.refreshToken()).accessToken())
                .isEqualTo("access-" + other.getId());
        // 폐기 뒤 다시 연동하면 새 워치 세션은 쓸 수 있다
        assertThat(auth.refresh(auth.issueWatchSession(m.getId()).refreshToken()).accessToken())
                .isEqualTo("access-" + m.getId());
    }

    @Test
    @DisplayName("로그아웃은 폰 갱신 토큰과 그 회원의 워치용 갱신 토큰을 함께 폐기한다")
    void logout_alsoRevokesWatchSessions() {
        Member m = social("로그아웃곰");
        TokenPair phone = tokens.issue(m.getId());
        TokenPair watch = auth.issueWatchSession(m.getId());

        auth.logout(m.getId(), phone.refreshToken());

        assertThat(tokens.isRevoked(phone.refreshToken())).isTrue();
        assertThatThrownBy(() -> auth.refresh(watch.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    @DisplayName("게스트의 워치 세션도 폐기한다")
    void guest_watchSessionsAreRevoked() {
        Member guest =
                repository.save(Member.guest(Nickname.of("러너0043"), requiredConsents(), NOW));
        TokenPair watch = auth.issueWatchSession(guest.getId());

        auth.revokeWatchSessions(guest.getId());

        assertThatThrownBy(() -> auth.refresh(watch.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    @DisplayName("탈퇴 사건 처리 중 폐기 저장소 장애는 삼키고 남긴다(탈퇴 회원은 갱신에서 어차피 막힌다)")
    void withdrawal_storeDown_isSwallowed() {
        Member m = social("장애곰");
        tokens.watchStoreDown();

        new WatchSessionWithdrawalListener(auth)
                .onMemberWithdrawn(new MemberWithdrawn(m.getId(), NOW));
    }

    @Test
    @DisplayName("BE13-2: 한 번도 폐기한 적 없는 회원이 탈퇴해도 세대 행을 새로 만들지 않는다")
    void withdrawal_doesNotCreateGenerationRow() {
        Member m = social("무행곰");
        auth.issueWatchSession(m.getId());

        new WatchSessionWithdrawalListener(auth)
                .onMemberWithdrawn(new MemberWithdrawn(m.getId(), NOW));

        assertThat(tokens.hasWatchGenerationRow(m.getId())).isFalse();
    }

    @Test
    @DisplayName("BE13-2: 이미 행이 있는 회원이 탈퇴하면 세대를 올려 워치 토큰을 끊는다")
    void withdrawal_advancesExistingRow() {
        Member m = social("유행곰");
        auth.revokeWatchSessions(m.getId());
        TokenPair watch = auth.issueWatchSession(m.getId());

        new WatchSessionWithdrawalListener(auth)
                .onMemberWithdrawn(new MemberWithdrawn(m.getId(), NOW));

        assertThat(tokens.isWatchSessionRevoked(watch.refreshToken())).isTrue();
    }

    @Test
    @DisplayName("BE13-2: 회원 자료를 파기하면(MemberPurged) 그 회원의 세대 행을 지운다")
    void purge_forgetsGenerationRow() {
        Member m = social("파기곰");
        auth.revokeWatchSessions(m.getId());
        assertThat(tokens.hasWatchGenerationRow(m.getId())).isTrue();

        new WatchSessionWithdrawalListener(auth).onMemberPurged(new MemberPurged(m.getId(), NOW));

        assertThat(tokens.hasWatchGenerationRow(m.getId())).isFalse();
    }

    @Test
    @DisplayName("BE13-2: 파기 중 세대 저장소 장애는 삼킨다(다음 파기 때 다시 지운다)")
    void purge_storeDown_isSwallowed() {
        Member m = social("장애파기곰");
        tokens.watchStoreDown();

        new WatchSessionWithdrawalListener(auth).onMemberPurged(new MemberPurged(m.getId(), NOW));
    }
}
