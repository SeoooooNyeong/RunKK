package com.a207.runkk.member;

import static com.a207.runkk.member.MemberFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditRecordView;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.audit.InMemoryAuditRepository;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AuthService(SQ-17 로그인 갈래)와 SignUpService(srs.md 6.3 1.2). */
class AuthAndSignUpServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    private final InMemoryMemberRepository repository = new InMemoryMemberRepository();
    private final FakeTokenIssuer tokens = new FakeTokenIssuer();
    private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
    private final AuditService auditService = new AuditService(auditRepository, clock);

    private SocialLoginGateway gateway(RuntimeException failure) {
        return new SocialLoginGateway() {
            @Override
            public SocialAccount authenticate(String providerToken) {
                if (failure != null) {
                    throw failure;
                }
                return new SocialAccount(ProviderType.KAKAO, providerToken);
            }

            @Override
            public ProviderType provider() {
                return ProviderType.KAKAO;
            }
        };
    }

    private AuthService auth(RuntimeException failure) {
        return new AuthService(List.of(gateway(failure)), repository, tokens);
    }

    private SignUpService signUp(ReservedNicknameChecker... reserved) {
        return new SignUpService(repository, List.of(reserved), tokens, auditService, clock);
    }

    @Test
    void login_newAccount_requiresSignUp_thenSignUpLogsIn() {
        LoginResult first = auth(null).login(ProviderType.KAKAO, "kakao-1");
        assertThat(first.isSignUpRequired()).isTrue();

        SignUpResult result =
                signUp().signUp(
                                new SignUpCommand(
                                        first.signUpTicket(), MemberFixtures.form("달리는곰")));
        assertThat(result.nickname()).isEqualTo("달리는곰");
        assertThat(result.tokens().accessToken()).isEqualTo("access-" + result.memberId());

        LoginResult second = auth(null).login(ProviderType.KAKAO, "kakao-1");
        assertThat(second.isLoggedIn()).isTrue();
        assertThat(second.tokens().accessToken()).isEqualTo("access-" + result.memberId());

        List<AuditRecordView> audits = auditRepository.all();
        assertThat(audits)
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.action()).isEqualTo(AuditAction.SIGN_UP_CONSENT);
                            assertThat(a.after())
                                    .contains("TERMS=true")
                                    .doesNotContain("HEART_RATE");
                        });
    }

    @Test
    void login_failures_becomeFailedResults() {
        assertThat(
                        auth(new ProviderUnavailableException("timeout"))
                                .login(ProviderType.KAKAO, "t")
                                .failure())
                .isEqualTo(ReasonCode.PROVIDER_UNAVAILABLE);
        assertThat(auth(new AuthFailureException("bad")).login(ProviderType.KAKAO, "t").failure())
                .isEqualTo(ReasonCode.AUTH_FAILED);
        assertThat(auth(null).login(ProviderType.GOOGLE, "t").failure())
                .isEqualTo(ReasonCode.PROVIDER_UNAVAILABLE);
    }

    @Test
    void refresh_rotatesAndRejectsReuseAndWithdrawnMember() {
        AuthService service = auth(null);
        String ticket = service.login(ProviderType.KAKAO, "k").signUpTicket();
        SignUpResult r = signUp().signUp(new SignUpCommand(ticket, MemberFixtures.form("달리는곰")));

        TokenPair renewed = service.refresh(r.tokens().refreshToken());
        assertThat(renewed.accessToken()).isEqualTo("access-" + r.memberId());
        assertThatThrownBy(() -> service.refresh(r.tokens().refreshToken()))
                .isInstanceOf(AuthFailureException.class);

        repository.findById(r.memberId()).orElseThrow().withdraw(NOW);
        assertThatThrownBy(() -> service.refresh(renewed.refreshToken()))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    void logout_revokesRefreshToken() {
        auth(null).logout(1L, "refresh-1-1");
        assertThat(tokens.isRevoked("refresh-1-1")).isTrue();
    }

    @Test
    void signUp_invalidTicket_andDuplicateAccount_areRejected() {
        assertReason(
                () -> signUp().signUp(new SignUpCommand("forged", MemberFixtures.form("곰곰"))),
                ReasonCode.SIGN_UP_TICKET_INVALID);

        String ticket = tokens.issueSignUpTicket(MemberFixtures.account("dup"));
        signUp().signUp(new SignUpCommand(ticket, MemberFixtures.form("곰곰")));
        assertReason(
                () -> signUp().signUp(new SignUpCommand(ticket, MemberFixtures.form("곰곰곰"))),
                ReasonCode.ALREADY_MEMBER);
    }

    @Test
    void signUp_takenOrReservedNickname_getsFourDigits() {
        signUp().signUp(
                        new SignUpCommand(
                                tokens.issueSignUpTicket(MemberFixtures.account("a")),
                                MemberFixtures.form("달리는곰")));
        SignUpResult second =
                signUp().signUp(
                                new SignUpCommand(
                                        tokens.issueSignUpTicket(MemberFixtures.account("b")),
                                        MemberFixtures.form("달리는곰")));
        assertThat(second.nickname()).matches("달리는곰\\d{4}");

        SignUpResult reserved =
                signUp(n -> n.equals("가상곰"))
                        .signUp(
                                new SignUpCommand(
                                        tokens.issueSignUpTicket(MemberFixtures.account("c")),
                                        MemberFixtures.form("가상곰")));
        assertThat(reserved.nickname()).matches("가상곰\\d{4}");
    }

    @Test
    void signUp_withoutNickname_usesDefaultNameWithDigits() {
        SignUpResult r =
                signUp().signUp(
                                new SignUpCommand(
                                        tokens.issueSignUpTicket(MemberFixtures.account("n")),
                                        MemberFixtures.form(null)));
        assertThat(r.nickname()).matches(SignUpService.DEFAULT_NICKNAME + "\\d{4}");
    }

    @Test
    void signUp_allSuffixesTaken_isRejected() {
        SignUpService service = signUp(n -> n.startsWith("곰곰"));
        assertReason(
                () ->
                        service.signUp(
                                new SignUpCommand(
                                        tokens.issueSignUpTicket(MemberFixtures.account("x")),
                                        MemberFixtures.form("곰곰"))),
                ReasonCode.NICKNAME_INVALID);
        assertThat(repository.size()).isZero();
        assertThat(auditRepository.size()).isZero();
    }

    @Test
    void signUp_decisionTableRejection_leavesNothing() {
        SignUpForm f = MemberFixtures.form("달리는곰");
        SignUpForm under14 =
                new SignUpForm(
                        f.nickname(),
                        2015,
                        f.gender(),
                        null,
                        null,
                        f.consents(),
                        f.recordVisibility(),
                        f.tags());
        assertReason(
                () ->
                        signUp().signUp(
                                        new SignUpCommand(
                                                tokens.issueSignUpTicket(
                                                        MemberFixtures.account("u")),
                                                under14)),
                ReasonCode.UNDER_14);
        assertThat(repository.size()).isZero();
    }

    private static void assertReason(Runnable action, ReasonCode reason) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(reason);
    }
}
