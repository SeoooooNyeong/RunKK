package com.a207.runkk.member;

import com.a207.runkk.common.ReasonCode;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 로그인 유스케이스를 조정한다(설계 6.1.2, SQ-17). 제공자는 SocialLoginGateway 다형성으로 고르고 제공자 토큰은 서버 자체 JWT로 바꾼다(D-19).
 * 제공자 호출(최대 수 초) 동안 트랜잭션을 잡지 않는다.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 설계의 Map<ProviderType, SocialLoginGateway>. 제공자는 호출 때 provider()로 고른다(빈 생성 순서와 무관). */
    private final List<SocialLoginGateway> gateways;

    private final MemberRepository memberRepository;
    private final TokenIssuer tokenIssuer;

    public AuthService(
            List<SocialLoginGateway> gateways,
            MemberRepository memberRepository,
            TokenIssuer tokenIssuer) {
        this.gateways = List.copyOf(gateways);
        this.memberRepository = memberRepository;
        this.tokenIssuer = tokenIssuer;
    }

    /**
     * 제공자 무응답 → failed(PROVIDER_UNAVAILABLE), 인증 실패 → failed(AUTH_FAILED), 회원 → loggedIn, 아니면
     * signUpRequired.
     */
    public LoginResult login(ProviderType provider, String providerToken) {
        SocialLoginGateway gateway =
                gateways.stream().filter(g -> g.provider() == provider).findFirst().orElse(null);
        if (gateway == null) {
            log.warn("login_failed provider={} reason=gateway_missing", provider);
            return LoginResult.failed(ReasonCode.PROVIDER_UNAVAILABLE);
        }
        SocialAccount account;
        try {
            account = gateway.authenticate(providerToken);
        } catch (ProviderUnavailableException e) {
            log.warn("login_failed provider={} reason=provider_unavailable", provider);
            return LoginResult.failed(ReasonCode.PROVIDER_UNAVAILABLE);
        } catch (AuthFailureException e) {
            log.info("login_failed provider={} reason=auth_failed", provider);
            return LoginResult.failed(ReasonCode.AUTH_FAILED);
        }
        return memberRepository
                .findByAccount(account)
                .map(member -> LoginResult.loggedIn(tokenIssuer.issue(member.getId())))
                .orElseGet(
                        () -> LoginResult.signUpRequired(tokenIssuer.issueSignUpTicket(account)));
    }

    /** 갱신 토큰 폐기(FR-002). 기기의 미전송 기록은 앱이 지우지 않는다. */
    public void logout(long memberId, String refreshToken) {
        tokenIssuer.revoke(refreshToken);
        log.info("logout memberId={}", memberId);
    }

    /**
     * 접근 토큰 재발급(D-19). 갱신 토큰은 한 번만 쓴다(회전). 탈퇴한 회원이면 AuthFailureException.
     *
     * @throws AuthFailureException 갱신 토큰이 위조·만료·폐기되었거나 회원이 탈퇴함
     * @throws ProviderUnavailableException 폐기 목록을 확인할 수 없음(fail-closed)
     */
    public TokenPair refresh(String refreshToken) {
        long memberId = tokenIssuer.redeemRefreshToken(refreshToken);
        boolean active =
                memberRepository
                        .findById(memberId)
                        .map(m -> m.getStatus() == MemberStatus.ACTIVE)
                        .orElse(false);
        if (!active) {
            throw new AuthFailureException("탈퇴했거나 없는 회원");
        }
        return tokenIssuer.issue(memberId);
    }

    /** 만료된 갱신 토큰 폐기 목록 행 정리(M6 후속 결정). batch.member의 RevocationPurgeJob이 3시에 부른다. 지운 수. */
    public int purgeExpiredRevocations(LocalDateTime now) {
        return tokenIssuer.purgeExpiredRevocations(now);
    }
}
