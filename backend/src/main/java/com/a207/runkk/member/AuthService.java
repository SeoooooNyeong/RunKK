package com.a207.runkk.member;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로그인·게스트 세션 재개·소셜 연동 유스케이스를 조정한다(설계 6.1.2, SQ-17, SQ-18). 제공자는 SocialLoginGateway 다형성으로 고르고 제공자
 * 토큰은 서버 자체 JWT로 바꾼다(D-19). 제공자 호출(최대 수 초) 동안 트랜잭션을 잡지 않고, 회원 행을 고치는 짧은 단계만 트랜잭션으로 감싼다. 마지막 접속
 * 기록(Member.recordAccess)은 로그인·토큰 갱신·게스트 세션 재개 때 날짜가 바뀐 경우에만 쓰고(D-89 (3)), 기록 실패가 인증을 막지 않는다.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 소셜 계정 고유 제약 이름(V3). */
    private static final String ACCOUNT_UNIQUE = "uq_member_account";

    /** 설계의 Map<ProviderType, SocialLoginGateway>. 제공자는 호출 때 provider()로 고른다(빈 생성 순서와 무관). */
    private final List<SocialLoginGateway> gateways;

    private final MemberRepository memberRepository;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;
    private final TransactionOperations transactions;

    @Autowired
    public AuthService(
            List<SocialLoginGateway> gateways,
            MemberRepository memberRepository,
            TokenIssuer tokenIssuer,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this(
                gateways,
                memberRepository,
                tokenIssuer,
                clock,
                new TransactionTemplate(transactionManager));
    }

    /** 시험은 TransactionOperations.withoutTransaction()을 넘긴다. */
    AuthService(
            List<SocialLoginGateway> gateways,
            MemberRepository memberRepository,
            TokenIssuer tokenIssuer,
            Clock clock,
            TransactionOperations transactions) {
        this.gateways = List.copyOf(gateways);
        this.memberRepository = memberRepository;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
        this.transactions = transactions;
    }

    /**
     * 제공자 무응답 → failed(PROVIDER_UNAVAILABLE), 인증 실패 → failed(AUTH_FAILED), 회원 → recordAccess 뒤
     * loggedIn, 아니면 signUpRequired.
     */
    public LoginResult login(ProviderType provider, String providerToken) {
        Optional<SocialLoginGateway> gateway = gatewayOf(provider);
        if (gateway.isEmpty()) {
            log.warn("login_failed provider={} reason=gateway_missing", provider);
            return LoginResult.failed(ReasonCode.PROVIDER_UNAVAILABLE);
        }
        SocialAccount account;
        try {
            account = gateway.get().authenticate(providerToken);
        } catch (ProviderUnavailableException e) {
            log.warn("login_failed provider={} reason=provider_unavailable", provider);
            return LoginResult.failed(ReasonCode.PROVIDER_UNAVAILABLE);
        } catch (AuthFailureException e) {
            log.info("login_failed provider={} reason=auth_failed", provider);
            return LoginResult.failed(ReasonCode.AUTH_FAILED);
        }
        Optional<Member> member = memberRepository.findByAccount(account);
        if (member.isEmpty()) {
            return LoginResult.signUpRequired(tokenIssuer.issueSignUpTicket(account));
        }
        long memberId = member.get().getId();
        recordAccess(memberId);
        return LoginResult.loggedIn(tokenIssuer.issue(memberId));
    }

    /**
     * 갱신 토큰 폐기(FR-002)와 그 회원의 워치용 갱신 토큰 일괄 폐기(W25). 기기의 미전송 기록은 앱이 지우지 않는다.
     *
     * @throws ProviderUnavailableException 폐기 목록·워치 세션 세대를 기록할 수 없음(다시 불러도 된다)
     */
    public void logout(long memberId, String refreshToken) {
        tokenIssuer.revoke(refreshToken);
        tokenIssuer.revokeWatchSessions(memberId);
        log.info("logout memberId={}", memberId);
    }

    /**
     * 그 회원의 워치용 갱신 토큰을 모두 폐기한다(DELETE /auth/watch-session·로그아웃·탈퇴, 2026-11-01 사용자 결정 W25). 폰 토큰은
     * 그대로다. 워치의 접근 토큰은 만료(최대 1시간)까지 남는다.
     *
     * @throws ProviderUnavailableException 워치 세션 세대를 기록할 수 없음
     */
    public void revokeWatchSessions(long memberId) {
        tokenIssuer.revokeWatchSessions(memberId);
        log.info("watch_sessions_revoked memberId={}", memberId);
    }

    /**
     * 탈퇴한 회원의 워치용 갱신 토큰을 폐기한다. 세대 행이 없으면 만들지 않는다(BE13-2).
     *
     * @throws ProviderUnavailableException 워치 세션 세대를 기록할 수 없음
     */
    public void revokeWatchSessionsOfWithdrawn(long memberId) {
        tokenIssuer.revokeExistingWatchSessions(memberId);
        log.info("watch_sessions_revoked_on_withdrawal memberId={}", memberId);
    }

    /**
     * 회원 자료 파기 때 그 회원의 워치 세션 세대 행을 지운다(BE13-2).
     *
     * @throws ProviderUnavailableException 워치 세션 세대를 지울 수 없음
     */
    public void forgetWatchSessions(long memberId) {
        tokenIssuer.forgetWatchSessions(memberId);
    }

    /**
     * 접근 토큰 재발급(D-19). 갱신 토큰은 한 번만 쓴다(회전). 워치용 갱신 토큰이면 새 쌍도 워치용이다(W25). 탈퇴한 회원이면
     * AuthFailureException. 발급 때 마지막 접속을 기록한다.
     *
     * @throws AuthFailureException 갱신 토큰이 위조·만료·폐기되었거나 회원이 탈퇴함
     * @throws ProviderUnavailableException 폐기 목록을 확인할 수 없음(fail-closed)
     */
    public TokenPair refresh(String refreshToken) {
        RefreshGrant grant = tokenIssuer.redeemRefreshToken(refreshToken);
        requireActive(grant.memberId());
        recordAccess(grant.memberId());
        return tokenIssuer.reissue(grant);
    }

    /**
     * POST /auth/watch-session(FR-240, D-372, SQ-43). 인증된 회원(게스트 포함)에게 로그인 때와 같은 issue로 새 토큰 쌍(접근
     * 1시간, 갱신 14일)을 준다. 워치는 자기 갱신 토큰으로 따로 회전하므로 폰의 토큰 쌍은 폐기하지 않는다. 마지막 접속은 기록하지 않는다(폰의 로그인·갱신이 이미
     * 기록한다). 갱신 토큰은 워치용으로 표시해 revokeWatchSessions가 폰 토큰과 나눠 폐기한다(W25).
     *
     * @throws AuthFailureException 탈퇴했거나 없는 회원
     * @throws ProviderUnavailableException 워치 세션 세대를 읽을 수 없음
     */
    public TokenPair issueWatchSession(long memberId) {
        requireActive(memberId);
        TokenPair pair = tokenIssuer.issueWatch(memberId);
        log.info("watch_session_issued memberId={}", memberId);
        return pair;
    }

    private void requireActive(long memberId) {
        boolean active =
                memberRepository
                        .findById(memberId)
                        .map(m -> m.getStatus() == MemberStatus.ACTIVE)
                        .orElse(false);
        if (!active) {
            throw new AuthFailureException("탈퇴했거나 없는 회원");
        }
    }

    /**
     * POST /auth/guest/session(FR-197, D-89). GuestCredential.parse → 회원 조회 → matchesGuestSecret(상수
     * 시간 비교) → 토큰 발급, recordAccess(now). 형식 위조·해시 불일치·연동한 회원·탈퇴 회원·없는 회원은 모두 같은
     * AuthFailureException(사유를 구분해 알리지 않는다).
     */
    public TokenPair resumeGuest(String credential, LocalDateTime now) {
        GuestCredential.Parsed parsed =
                GuestCredential.parse(credential).orElseThrow(AuthService::guestRejected);
        Member member =
                memberRepository
                        .findById(parsed.memberId())
                        .orElseThrow(AuthService::guestRejected);
        if (member.getStatus() != MemberStatus.ACTIVE
                || !member.matchesGuestSecret(parsed.secret())) {
            throw guestRejected();
        }
        recordAccess(parsed.memberId(), now);
        return tokenIssuer.issue(parsed.memberId());
    }

    /**
     * POST /me/social-account(FR-198, D-90). SocialLoginGateway.authenticate(실패
     * AuthFailureException, 무응답 ProviderUnavailableException) → 그 계정이 다른 회원과 연동돼 있으면
     * ALREADY_MEMBER(합치지 않는다, 자기 자신의 계정이면 건너뛴다) → Member.linkSocialAccount(이미 LINKED면
     * ALREADY_LINKED) → 저장. 토큰은 바꾸지 않는다.
     */
    public SocialLinkResult linkSocialAccount(
            long memberId, ProviderType provider, String providerToken) {
        SocialLoginGateway gateway =
                gatewayOf(provider)
                        .orElseThrow(() -> new ProviderUnavailableException("제공자 연동 없음"));
        SocialAccount account = gateway.authenticate(providerToken);
        try {
            transactions.executeWithoutResult(
                    status -> {
                        if (memberRepository
                                .findByAccount(account)
                                .filter(owner -> !Objects.equals(owner.getId(), memberId))
                                .isPresent()) {
                            throw new BusinessRuleException(ReasonCode.ALREADY_MEMBER);
                        }
                        Member member = MemberLoader.load(memberRepository, memberId);
                        member.linkSocialAccount(account);
                        memberRepository.save(member);
                    });
        } catch (DataIntegrityViolationException e) {
            // 같은 소셜 계정을 동시에 연동한 경합: 고유 제약(uq_member_account)이 늦은 쪽을 막는다. 다른 제약 위반은 그대로 올린다
            if (violates(e, ACCOUNT_UNIQUE)) {
                throw new BusinessRuleException(ReasonCode.ALREADY_MEMBER);
            }
            throw e;
        }
        log.info("social_linked memberId={} provider={}", memberId, provider);
        return new SocialLinkResult(AccountType.LINKED, provider);
    }

    /** 만료된 갱신 토큰 폐기 목록 행 정리(M6 후속 결정). batch.member의 RevocationPurgeJob이 3시에 부른다. 지운 수. */
    public int purgeExpiredRevocations(LocalDateTime now) {
        return tokenIssuer.purgeExpiredRevocations(now);
    }

    private Optional<SocialLoginGateway> gatewayOf(ProviderType provider) {
        return gateways.stream().filter(g -> g.provider() == provider).findFirst();
    }

    private void recordAccess(long memberId) {
        recordAccess(memberId, LocalDateTime.now(clock));
    }

    /** 마지막 접속 기록(날짜가 바뀐 때만 쓴다). 저장 실패(낙관적 잠금 충돌 등)는 인증을 막지 않고 운영 로그만 남긴다. */
    private void recordAccess(long memberId, LocalDateTime now) {
        try {
            transactions.executeWithoutResult(
                    status ->
                            memberRepository
                                    .findById(memberId)
                                    .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                                    .filter(m -> m.recordAccess(now))
                                    .ifPresent(memberRepository::save));
        } catch (DataAccessException | TransactionException e) {
            log.warn(
                    "record_access_failed memberId={} error={}",
                    memberId,
                    e.getClass().getSimpleName());
        }
    }

    private static boolean violates(Throwable e, String constraint) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    private static AuthFailureException guestRejected() {
        return new AuthFailureException("게스트 자격 증명 거부");
    }
}
