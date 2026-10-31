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

/** 워치용 토큰 쌍 발급(D-372, FR-240, SQ-43): AuthService.issueWatchSession. 폰 토큰을 무효로 만들지 않는다. */
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
}
