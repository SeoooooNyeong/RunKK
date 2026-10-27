package com.a207.runkk.member;

import static com.a207.runkk.member.MemberFixtures.requiredConsents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditRecordView;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.audit.InMemoryAuditRepository;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.ReasonCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/**
 * v0.11 회원 응용 서비스(test.md 2.1.10.4 TC-6034~TC-6038, 2.1.10.5 TC-6039~TC-6045, 2.1.10.18
 * TC-6188~TC-6191, 2.1.10.23 TC-6218): 게스트 시작·세션 재개·소셜 연동·마지막 접속·미이용 게스트 파기.
 */
@DisplayName("v0.11 회원 서비스: 게스트 시작·재개·연동·미이용 파기")
class GuestServicesTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime N = LocalDateTime.of(2026, 10, 22, 19, 0);

    private final Clock clock = Clock.fixed(N.atZone(SEOUL).toInstant(), SEOUL);
    private final InMemoryMemberRepository repository = new InMemoryMemberRepository();
    private final FakeTokenIssuer tokens = new FakeTokenIssuer();
    private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
    private final AuditService auditService = new AuditService(auditRepository, clock);
    private final List<DomainEvent> published = new ArrayList<>();

    /** 정수는 정한 순서대로, 바이트는 고정 값으로 내는 SecureRandom 대역. */
    static final class ScriptedRandom extends SecureRandom {
        private final Deque<Integer> ints;
        private final List<Integer> bounds = new ArrayList<>();

        ScriptedRandom(Integer... ints) {
            this.ints = new ArrayDeque<>(Arrays.asList(ints));
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return ints.isEmpty() ? 0 : ints.poll();
        }

        @Override
        public void nextBytes(byte[] bytes) {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private SignUpService signUpService(SecureRandom random, ReservedNicknameChecker... reserved) {
        return new SignUpService(
                repository, List.of(reserved), tokens, auditService, clock, random);
    }

    private static GuestStartForm guestForm() {
        return new GuestStartForm(requiredConsents());
    }

    private static Member member(String nickname, LocalDateTime joined) {
        return Member.signUp(
                new SocialAccount(ProviderType.KAKAO, "k-" + nickname),
                new SignUpForm(null, requiredConsents()),
                Nickname.of(nickname),
                joined);
    }

    // ---- startAsGuest ----

    @Test
    @DisplayName("TC-6034 필수 동의 거부: 저장·감사·토큰·닉네임 조회 0")
    void startAsGuest_consentRejected() {
        ScriptedRandom random = new ScriptedRandom(42);
        Map<ConsentItem, Boolean> noTerms = requiredConsents();
        noTerms.put(ConsentItem.TERMS, false);
        assertThatThrownBy(() -> signUpService(random).startAsGuest(new GuestStartForm(noTerms)))
                .satisfies(
                        e ->
                                assertThat(((BusinessRuleException) e).reason())
                                        .isEqualTo(ReasonCode.CONSENT_MISSING));
        Map<ConsentItem, Boolean> under = requiredConsents();
        under.put(ConsentItem.AGE_14_PLUS, false);
        assertThatThrownBy(() -> signUpService(random).startAsGuest(new GuestStartForm(under)))
                .satisfies(
                        e ->
                                assertThat(((BusinessRuleException) e).reason())
                                        .isEqualTo(ReasonCode.UNDER_14));
        assertThat(repository.size()).isZero();
        assertThat(auditRepository.all()).isEmpty();
        assertThat(random.bounds).isEmpty();
    }

    @Test
    @DisplayName("TC-6035 정상: 러너0042, g1.<id>.<43자>, GUEST·해시·감사·토큰")
    void startAsGuest_success() {
        GuestStartResult result = signUpService(new ScriptedRandom(42)).startAsGuest(guestForm());

        assertThat(result.nickname()).isEqualTo("러너0042");
        String secret = "A".repeat(43);
        assertThat(result.guestCredential()).isEqualTo("g1." + result.memberId() + "." + secret);
        assertThat(result.tokens().accessToken()).isEqualTo("access-" + result.memberId());
        Member saved = repository.findById(result.memberId()).orElseThrow();
        assertThat(saved.getAccountType()).isEqualTo(AccountType.GUEST);
        assertThat(saved.account()).isNull();
        assertThat(saved.lastAccessAt()).isEqualTo(N);
        assertThat(saved.guestCredentialHash()).isEqualTo(GuestCredential.hashOf(secret));
        List<AuditRecordView> audits = auditRepository.all();
        assertThat(audits)
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.action()).isEqualTo(AuditAction.SIGN_UP_CONSENT);
                            assertThat(a.actorRole()).isEqualTo(ActorRole.RUNNER);
                            assertThat(String.valueOf(a.before()) + a.after())
                                    .doesNotContain(secret);
                        });
    }

    @Test
    @DisplayName("TC-6036 회원·가상 닉네임과 겹치면 다른 숫자")
    void startAsGuest_retriesOnTaken() {
        repository.save(member("러너0042", N));
        assertThat(signUpService(new ScriptedRandom(42, 43)).startAsGuest(guestForm()).nickname())
                .isEqualTo("러너0043");

        InMemoryMemberRepository fresh = new InMemoryMemberRepository();
        SignUpService reserved =
                new SignUpService(
                        fresh,
                        List.of(n -> n.equals("러너0042")),
                        tokens,
                        auditService,
                        clock,
                        new ScriptedRandom(42, 43));
        assertThat(reserved.startAsGuest(guestForm()).nickname()).isEqualTo("러너0043");
    }

    @Test
    @DisplayName("TC-6037 4자리 20번 → 5자리 5번 → TEMPORARILY_UNAVAILABLE")
    void startAsGuest_exhaustion() {
        Integer[] nineteenTaken = IntStream.range(0, 19).boxed().toArray(Integer[]::new);
        for (int i = 0; i < 19; i++) {
            repository.save(member(Nickname.numbered(i).value(), N));
        }
        Integer[] first = Arrays.copyOf(nineteenTaken, 20);
        first[19] = 777;
        assertThat(signUpService(new ScriptedRandom(first)).startAsGuest(guestForm()).nickname())
                .isEqualTo("러너0777");

        repository.save(member("러너0019", N));
        Integer[] all20 = IntStream.range(0, 20).boxed().toArray(Integer[]::new);
        Integer[] second = Arrays.copyOf(all20, 21);
        second[20] = 12345 - 10_000;
        ScriptedRandom r2 = new ScriptedRandom(second);
        assertThat(signUpService(r2).startAsGuest(guestForm()).nickname()).isEqualTo("러너12345");
        assertThat(r2.bounds.subList(0, 20)).containsOnly(10_000);
        assertThat(r2.bounds.get(20)).isEqualTo(90_000);

        for (int n = 10001; n <= 10004; n++) {
            repository.save(member(Nickname.numbered(n).value(), N));
        }
        Integer[] third = Arrays.copyOf(all20, 25);
        third[20] = 1;
        third[21] = 2;
        third[22] = 3;
        third[23] = 4;
        third[24] = 54321 - 10_000;
        assertThat(signUpService(new ScriptedRandom(third)).startAsGuest(guestForm()).nickname())
                .isEqualTo("러너54321");

        repository.save(member("러너10000", N));
        Integer[] fourth = Arrays.copyOf(all20, 25);
        for (int i = 20; i < 25; i++) {
            fourth[i] = i - 20;
        }
        int before = repository.size();
        int audits = auditRepository.all().size();
        assertThatThrownBy(
                        () -> signUpService(new ScriptedRandom(fourth)).startAsGuest(guestForm()))
                .satisfies(
                        e ->
                                assertThat(((BusinessRuleException) e).reason())
                                        .isEqualTo(ReasonCode.TEMPORARILY_UNAVAILABLE));
        assertThat(repository.size()).isEqualTo(before);
        assertThat(auditRepository.all()).hasSize(audits);
    }

    @Test
    @DisplayName("TC-6038 ① 선택 동의: 빠진 MARKETING은 거짓으로 남고 심박·얼굴은 거짓")
    void startAsGuest_optionalConsents() {
        Map<ConsentItem, Boolean> consents = requiredConsents();
        consents.put(ConsentItem.LLM_TRANSFER, true);
        long id =
                signUpService(new ScriptedRandom(1))
                        .startAsGuest(new GuestStartForm(consents))
                        .memberId();
        Member m = repository.findById(id).orElseThrow();
        assertThat(m.hasConsent(ConsentItem.LLM_TRANSFER)).isTrue();
        assertThat(m.hasConsent(ConsentItem.MARKETING)).isFalse();
        assertThat(m.hasConsent(ConsentItem.HEART_RATE)).isFalse();
        assertThat(m.hasConsent(ConsentItem.FACE_TRANSFER)).isFalse();
        assertThat(m.consentRecords())
                .anySatisfy(
                        r -> {
                            assertThat(r.item()).isEqualTo(ConsentItem.MARKETING);
                            assertThat(r.agreed()).isFalse();
                        });
    }

    // ---- signUp nickname (D-143, D-164) ----

    private SignUpResult signUpWith(String nickname, ScriptedRandom random) {
        String ticket =
                tokens.issueSignUpTicket(
                        new SocialAccount(ProviderType.KAKAO, "signup-" + nickname));
        return signUpService(random)
                .signUp(new SignUpCommand(ticket, new SignUpForm(nickname, requiredConsents())));
    }

    @Test
    @DisplayName("TC-6218 가입 닉네임: 제공자 이름을 고치고 NICKNAME_INVALID를 던지지 않는다")
    void signUp_providerName() {
        assertThat(signUpWith("달리기왕", new ScriptedRandom()).nickname()).isEqualTo("달리기왕");
        assertThat(signUpWith("가", new ScriptedRandom(5)).nickname()).isEqualTo("러너0005");
        assertThat(signUpWith("나".repeat(25), new ScriptedRandom()).nickname())
                .isEqualTo("나".repeat(20));
        assertThat(signUpWith("", new ScriptedRandom(6)).nickname()).isEqualTo("러너0006");
        String twenty = "다".repeat(20);
        repository.save(member(twenty, N));
        assertThat(signUpWith(twenty, new ScriptedRandom(7)).nickname())
                .isEqualTo("다".repeat(16) + "0007");
    }

    @Test
    @DisplayName("TC-6037 가입도 자동 닉네임 소진이면 TEMPORARILY_UNAVAILABLE")
    void signUp_autoNicknameExhausted() {
        repository.save(member("러너0000", N));
        repository.save(member("러너10000", N));
        assertThatThrownBy(() -> signUpWith(null, new ScriptedRandom()))
                .satisfies(
                        e ->
                                assertThat(((BusinessRuleException) e).reason())
                                        .isEqualTo(ReasonCode.TEMPORARILY_UNAVAILABLE));
    }

    // ---- AuthService ----

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
        return new AuthService(
                List.of(gateway(failure)),
                repository,
                tokens,
                clock,
                TransactionOperations.withoutTransaction());
    }

    private GuestStartResult startedGuest() {
        return signUpService(new ScriptedRandom(42)).startAsGuest(guestForm());
    }

    @Test
    @DisplayName("TC-6189 linkSocialAccount: 연동·실패·ALREADY_MEMBER·ALREADY_LINKED")
    void linkSocialAccount() {
        long g = startedGuest().memberId();
        int issuedBefore = tokens.issuedCount();

        assertThatThrownBy(
                        () ->
                                auth(new AuthFailureException("bad"))
                                        .linkSocialAccount(g, ProviderType.KAKAO, "k-77"))
                .isInstanceOf(AuthFailureException.class);
        assertThatThrownBy(
                        () ->
                                auth(new ProviderUnavailableException("timeout"))
                                        .linkSocialAccount(g, ProviderType.KAKAO, "k-77"))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThat(repository.findById(g).orElseThrow().isGuest()).isTrue();

        long other = repository.save(member("다른곰", N)).getId();
        assertThatThrownBy(() -> auth(null).linkSocialAccount(g, ProviderType.KAKAO, "k-다른곰"))
                .satisfies(
                        e ->
                                assertThat(((BusinessRuleException) e).reason())
                                        .isEqualTo(ReasonCode.ALREADY_MEMBER));

        SocialLinkResult linked = auth(null).linkSocialAccount(g, ProviderType.KAKAO, "k-77");
        assertThat(linked).isEqualTo(new SocialLinkResult(AccountType.LINKED, ProviderType.KAKAO));
        assertThat(tokens.issuedCount()).isEqualTo(issuedBefore);
        assertThat(repository.findById(g).orElseThrow().guestCredentialHash()).isNull();

        // 응답이 유실된 재시도: 자기 계정으로 다시 연동하면 ALREADY_MEMBER가 아니라 ALREADY_LINKED
        assertThatThrownBy(() -> auth(null).linkSocialAccount(g, ProviderType.KAKAO, "k-77"))
                .satisfies(
                        e ->
                                assertThat(((BusinessRuleException) e).reason())
                                        .isEqualTo(ReasonCode.ALREADY_LINKED));

        assertThatThrownBy(() -> auth(null).linkSocialAccount(g, ProviderType.KAKAO, "k-다른곰"))
                .satisfies(
                        e ->
                                assertThat(((BusinessRuleException) e).reason())
                                        .isEqualTo(ReasonCode.ALREADY_MEMBER));
        assertThatThrownBy(() -> auth(null).linkSocialAccount(g, ProviderType.KAKAO, "k-new"))
                .satisfies(
                        e ->
                                assertThat(((BusinessRuleException) e).reason())
                                        .isEqualTo(ReasonCode.ALREADY_LINKED));
        assertThat(repository.findById(other).orElseThrow().getAccountType())
                .isEqualTo(AccountType.LINKED);
    }

    @Test
    @DisplayName("TC-6190 resumeGuest: 정상만 토큰, 나머지는 같은 AuthFailureException")
    void resumeGuest() {
        GuestStartResult started =
                new SignUpService(
                                repository,
                                List.of(),
                                tokens,
                                auditService,
                                Clock.offset(clock, Duration.ofDays(-1)),
                                new ScriptedRandom(42))
                        .startAsGuest(guestForm());
        long g = started.memberId();
        String credential = started.guestCredential();
        AuthService service = auth(null);

        TokenPair pair = service.resumeGuest(credential, N);
        assertThat(pair.accessToken()).isEqualTo("access-" + g);
        assertThat(repository.findById(g).orElseThrow().lastAccessAt()).isEqualTo(N);

        GuestStartResult other = signUpService(new ScriptedRandom(43)).startAsGuest(guestForm());
        service.linkSocialAccount(other.memberId(), ProviderType.KAKAO, "k-linked");
        GuestStartResult gone = signUpService(new ScriptedRandom(44)).startAsGuest(guestForm());
        repository.findById(gone.memberId()).orElseThrow().withdraw(N);

        int issued = tokens.issuedCount();
        String forgedSecret = credential.substring(0, credential.length() - 1) + "B";
        assertFails(service, "g1.x.y");
        assertFails(service, forgedSecret);
        assertFails(service, "g1.999." + "A".repeat(43));
        assertFails(service, other.guestCredential());
        assertFails(service, gone.guestCredential());
        assertThat(tokens.issuedCount()).isEqualTo(issued);
    }

    private static void assertFails(AuthService service, String credential) {
        assertThatThrownBy(() -> service.resumeGuest(credential, N))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    @DisplayName("TC-6191 login·refresh: 날짜가 바뀐 때만 lastAccessAt을 쓴다")
    void loginAndRefresh_recordAccess() {
        LocalDateTime yesterday = N.minusDays(1);
        Member m = repository.save(member("접속곰", yesterday));
        auth(null).login(ProviderType.KAKAO, "k-접속곰");
        assertThat(m.lastAccessAt()).isEqualTo(N);

        Member early = repository.save(member("새벽곰", N.withHour(7)));
        TokenPair pair = tokens.issue(early.getId());
        auth(null).refresh(pair.refreshToken());
        assertThat(early.lastAccessAt()).isEqualTo(N.withHour(7));
    }

    // ---- settingsOf ----

    @Test
    @DisplayName("TC-6188 settingsOf: 계정 유형·연동 제공자·테마·근접 알림")
    void settingsOf() {
        MemberSettingService settings =
                new MemberSettingService(
                        repository,
                        auditService,
                        published::add,
                        new LegalDocumentCatalog(
                                "https://t", "https://p", "https://l", "https://h"),
                        clock);
        long g = startedGuest().memberId();
        Member linked =
                repository.save(
                        Member.signUp(
                                new SocialAccount(ProviderType.GOOGLE, "g-1"),
                                new SignUpForm(null, requiredConsents()),
                                Nickname.of("구글곰"),
                                N));

        MemberSettingsView guestView = settings.settingsOf(g);
        assertThat(guestView.accountType()).isEqualTo(AccountType.GUEST);
        assertThat(guestView.linkedProvider()).isNull();
        assertThat(guestView.theme()).isEqualTo(ScreenTheme.SYSTEM);
        assertThat(guestView.battleSettings().proximityAlert()).isTrue();
        MemberSettingsView linkedView = settings.settingsOf(linked.getId());
        assertThat(linkedView.accountType()).isEqualTo(AccountType.LINKED);
        assertThat(linkedView.linkedProvider()).isEqualTo(ProviderType.GOOGLE);
        assertThat(MemberSummary.class.getRecordComponents())
                .extracting(c -> c.getName())
                .doesNotContain("accountType", "tags");
    }

    // ---- withdrawIdleGuests ----

    private WithdrawalService withdrawal(
            List<WithdrawalGuard> guards, List<IdleGuestWithdrawalGuard> idleGuards) {
        return new WithdrawalService(
                repository, guards, idleGuards, auditService, published::add, clock);
    }

    private long guestLastSeen(String nickname, LocalDateTime lastAccess) {
        Member g = Member.guest(Nickname.of(nickname), requiredConsents(), lastAccess);
        repository.save(g);
        g.attachGuestCredential(GuestCredential.hashOf("s-" + nickname));
        return g.getId();
    }

    @Test
    @DisplayName("TC-6039 대상 없음: 0, 기준 now − 365일")
    void idle_none() {
        assertThat(withdrawal(List.of(), List.of()).withdrawIdleGuests(N)).isZero();
        assertThat(published).isEmpty();
        assertThat(auditRepository.all()).isEmpty();
        assertThat(N.minus(WithdrawalService.GUEST_IDLE_LIMIT))
                .isEqualTo(LocalDateTime.of(2025, 10, 22, 19, 0));
    }

    @Test
    @DisplayName("TC-6040 경계: 365일 정각 포함, 1초 뒤 제외")
    void idle_boundary() {
        LocalDateTime cut = N.minusDays(365);
        long a = guestLastSeen("러너0001", cut.minusSeconds(1));
        long b = guestLastSeen("러너0002", cut);
        long c = guestLastSeen("러너0003", cut.plusSeconds(1));

        assertThat(withdrawal(List.of(), List.of()).withdrawIdleGuests(N)).isEqualTo(2);
        assertThat(repository.findById(a).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(repository.findById(b).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(repository.findById(c).orElseThrow().getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("TC-6042 가드가 사유를 주면 건너뛰고 다음 날 다시 본다")
    void idle_guardSkipsThenNextDay() {
        long g = guestLastSeen("러너0001", N.minusYears(2));
        AtomicInteger calls = new AtomicInteger();
        IdleGuestWithdrawalGuard battle =
                id ->
                        calls.getAndIncrement() == 0
                                ? Optional.of(ReasonCode.BATTLE_IN_PROGRESS)
                                : Optional.empty();
        WithdrawalService service = withdrawal(List.of(), List.of(battle));

        assertThat(service.withdrawIdleGuests(N)).isZero();
        assertThat(repository.findById(g).orElseThrow().getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(published).isEmpty();
        assertThat(service.withdrawIdleGuests(N.plusDays(1))).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-6043·TC-6041 파기: WITHDRAWN·해시 없음·감사 system:idle-guest·사건 1")
    void idle_withdrawsAuditsAndPublishes() {
        long g = guestLastSeen("러너0042", N.minusYears(2));
        assertThat(withdrawal(List.of(), List.of(id -> Optional.empty())).withdrawIdleGuests(N))
                .isEqualTo(1);
        Member m = repository.findById(g).orElseThrow();
        assertThat(m.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(m.nickname()).isNull();
        assertThat(m.guestCredentialHash()).isNull();
        assertThat(published).containsExactly(new MemberWithdrawn(g, N));
        assertThat(auditRepository.all())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.action()).isEqualTo(AuditAction.WITHDRAWAL);
                            assertThat(a.actorId()).isEqualTo("system:idle-guest");
                            assertThat(a.actorRole()).isEqualTo(ActorRole.SYSTEM);
                            assertThat(a.targetId()).isEqualTo(String.valueOf(g));
                        });
    }

    @Test
    @DisplayName("한 게스트의 커밋 뒤 수신자 실패가 다른 게스트 파기를 막지 않는다(게스트마다 따로 처리)")
    void idle_failureOfOneGuestDoesNotStopOthers() {
        long first = guestLastSeen("러너0001", N.minusYears(2));
        long second = guestLastSeen("러너0002", N.minusYears(2));
        WithdrawalService service =
                new WithdrawalService(
                        repository,
                        List.of(),
                        List.of(),
                        auditService,
                        event -> {
                            if (event instanceof MemberWithdrawn w && w.memberId() == first) {
                                throw new IllegalStateException("수신자 실패");
                            }
                            published.add(event);
                        },
                        clock);

        assertThat(service.withdrawIdleGuests(N)).isEqualTo(1);
        assertThat(repository.findById(second).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(published).containsExactly(new MemberWithdrawn(second, N));
    }

    @Test
    @DisplayName("TC-6044·TC-6045 연동 회원·탈퇴 게스트 제외, 탈퇴 가드는 부르지 않는다")
    void idle_onlyActiveGuests_andIgnoresWithdrawalGuards() {
        repository.save(member("연동곰", N.minusYears(2)));
        long gone = guestLastSeen("러너0001", N.minusYears(2));
        repository.findById(gone).orElseThrow().withdraw(N.minusYears(1));
        long kept = guestLastSeen("러너0002", N.minusYears(2));
        guestLastSeen("러너0003", N.minusYears(2));
        guestLastSeen("러너0004", N.minusYears(2));
        AtomicInteger leaderGuardCalls = new AtomicInteger();
        WithdrawalGuard leaderGuard =
                id -> {
                    leaderGuardCalls.incrementAndGet();
                    return Optional.of(ReasonCode.LEADER_MUST_TRANSFER);
                };
        IdleGuestWithdrawalGuard battle =
                id -> id == kept ? Optional.of(ReasonCode.BATTLE_IN_PROGRESS) : Optional.empty();

        assertThat(withdrawal(List.of(leaderGuard), List.of(battle)).withdrawIdleGuests(N))
                .isEqualTo(2);
        assertThat(leaderGuardCalls).hasValue(0);
        assertThat(repository.findById(kept).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
    }
}
