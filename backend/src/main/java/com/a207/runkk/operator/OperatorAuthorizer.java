package com.a207.runkk.operator;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.ProviderUnavailableException;
import com.a207.runkk.member.SocialAccount;
import com.a207.runkk.member.SocialLoginGateway;
import com.a207.runkk.policy.OperationPolicy;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 운영자 화이트리스트를 검사한다(설계 6.1.12 OperatorAuthorizer, FR-173, D-42, D-50). 운영자 로그인(authenticate)과
 * /admin/* 요청마다의 확인(ensureOperator)만 맡고 업무 규칙을 갖지 않는다. 화이트리스트는 policy.OPERATOR_WHITELIST에서 매번 읽으므로
 * 운영자를 빼면 이미 발급한 토큰도 다음 요청부터 거부된다.
 */
@Component
public class OperatorAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(OperatorAuthorizer.class);

    private final List<SocialLoginGateway> gateways;
    private final OperatorTokenIssuer tokenIssuer;
    private final OperationPolicy policy;

    public OperatorAuthorizer(
            List<SocialLoginGateway> gateways,
            OperatorTokenIssuer tokenIssuer,
            OperationPolicy policy) {
        this.gateways = List.copyOf(gateways);
        this.tokenIssuer = tokenIssuer;
        this.policy = policy;
    }

    /**
     * 제공자 토큰으로 계정을 확인하고 화이트리스트에 있으면 관리툴 토큰을 발급한다. provider는 "KAKAO"·"GOOGLE" 문자열이다(모르는 값이면
     * IllegalArgumentException, design-review.md R26). 인증 실패·화이트리스트 밖이면 PermissionDeniedException,
     * 제공자 무응답은 PROVIDER_UNAVAILABLE.
     */
    public OperatorSession authenticate(String provider, String providerToken) {
        ProviderType type = ProviderType.valueOf(provider.trim().toUpperCase(Locale.ROOT));
        SocialLoginGateway gateway =
                gateways.stream()
                        .filter(g -> g.provider() == type)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("제공자 없음: " + type));
        SocialAccount account;
        try {
            account = gateway.authenticate(providerToken);
        } catch (AuthFailureException e) {
            throw new PermissionDeniedException("운영자 인증 실패");
        } catch (ProviderUnavailableException e) {
            throw new BusinessRuleException(ReasonCode.PROVIDER_UNAVAILABLE);
        }
        OperatorId operator = new OperatorId(account);
        requireWhitelisted(operator);
        log.info("operator_login provider={}", type);
        return new OperatorSession(tokenIssuer.issue(operator), operator.actorId());
    }

    /** 관리툴 토큰 확인(위조·만료면 거부) 뒤 화이트리스트에서 빠졌으면 거부한다. */
    public OperatorId ensureOperator(String adminToken) {
        OperatorId operator = tokenIssuer.verify(adminToken);
        requireWhitelisted(operator);
        return operator;
    }

    private void requireWhitelisted(OperatorId operator) {
        SocialAccount account = operator.account();
        if (!policy.operatorWhitelist()
                .contains(account.provider().name(), account.providerAccountId())) {
            throw new PermissionDeniedException("운영자 화이트리스트 밖");
        }
    }
}
