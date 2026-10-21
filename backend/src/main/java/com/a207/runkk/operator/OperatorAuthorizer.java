package com.a207.runkk.operator;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
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
 * 운영자 화이트리스트를 검사한다(설계 6.1.12 OperatorAuthorizer, FR-173, D-42, D-50). 운영자 로그인(authenticate, 성공·실패
 * 감사 L25)과 /admin/* 요청마다의 확인(ensureOperator)만 맡고 업무 규칙을 갖지 않는다. 화이트리스트는 policy.OPERATOR_WHITELIST에서
 * 매번 읽으므로 운영자를 빼면 이미 발급한 토큰도 다음 요청부터 거부된다.
 */
@Component
public class OperatorAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(OperatorAuthorizer.class);

    /** 확인하지 못한 행위자·제공자. */
    static final String UNKNOWN = "UNKNOWN";

    /** 성공 감사의 결과 값. */
    static final String SUCCESS = "SUCCESS";

    private final List<SocialLoginGateway> gateways;
    private final OperatorTokenIssuer tokenIssuer;
    private final OperationPolicy policy;
    private final AuditService auditService;

    public OperatorAuthorizer(
            List<SocialLoginGateway> gateways,
            OperatorTokenIssuer tokenIssuer,
            OperationPolicy policy,
            AuditService auditService) {
        this.gateways = List.copyOf(gateways);
        this.tokenIssuer = tokenIssuer;
        this.policy = policy;
        this.auditService = auditService;
    }

    /**
     * 제공자 토큰으로 계정을 확인하고 화이트리스트에 있으면 관리툴 토큰을 발급한다. provider는 "KAKAO"·"GOOGLE" 문자열이다(모르는 값이면
     * IllegalArgumentException, design-review.md R26). 인증 실패·화이트리스트 밖이면 PermissionDeniedException,
     * 제공자 무응답은 PROVIDER_UNAVAILABLE. 성공·실패를 모두 감사 기록으로 남긴다(코드 리뷰 2차 L25): 행위자 = 운영자 계정(확인하지 못했으면
     * "제공자:UNKNOWN" 또는 "UNKNOWN"), 대상 = 접속지, 변경 후 = 결과(SUCCESS 또는 실패 사유). 성공 감사를 남기지 못하면 토큰을 주지
     * 않는다.
     */
    public OperatorSession authenticate(String provider, String providerToken, String client) {
        ProviderType type = providerOf(provider, client);
        SocialLoginGateway gateway =
                gateways.stream().filter(g -> g.provider() == type).findFirst().orElse(null);
        if (gateway == null) {
            failed(UNKNOWN, client, LoginFailure.UNKNOWN_PROVIDER);
            throw new IllegalArgumentException("제공자 없음: " + type);
        }
        String unidentified = type.name() + ":" + UNKNOWN;
        SocialAccount account;
        try {
            account = gateway.authenticate(providerToken);
        } catch (AuthFailureException e) {
            failed(unidentified, client, LoginFailure.AUTH_FAILED);
            throw new PermissionDeniedException("운영자 인증 실패");
        } catch (ProviderUnavailableException e) {
            failed(unidentified, client, LoginFailure.PROVIDER_UNAVAILABLE);
            throw new BusinessRuleException(ReasonCode.PROVIDER_UNAVAILABLE);
        }
        OperatorId operator = new OperatorId(account);
        if (!isWhitelisted(operator)) {
            failed(operator.actorId(), client, LoginFailure.NOT_WHITELISTED);
            throw new PermissionDeniedException("운영자 화이트리스트 밖");
        }
        auditService.record(
                new AuditEntry(
                        operator.actorId(),
                        ActorRole.OPERATOR,
                        AuditAction.OPERATOR_LOGIN_SUCCESS,
                        client,
                        null,
                        SUCCESS));
        log.info("operator_login provider={}", type);
        return new OperatorSession(tokenIssuer.issue(operator), operator.actorId());
    }

    private ProviderType providerOf(String provider, String client) {
        try {
            return ProviderType.valueOf(provider.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            failed(UNKNOWN, client, LoginFailure.UNKNOWN_PROVIDER);
            throw e;
        }
    }

    /** 실패 감사. 감사 저장이 실패해도 원래 거부 사유를 가리지 않는다(오류 로그만). */
    private void failed(String actorId, String client, LoginFailure reason) {
        log.info("operator_login_failed reason={}", reason);
        try {
            auditService.record(
                    new AuditEntry(
                            actorId,
                            ActorRole.OPERATOR,
                            AuditAction.OPERATOR_LOGIN_FAILURE,
                            client,
                            null,
                            reason.name()));
        } catch (RuntimeException e) {
            log.error(
                    "operator_login_audit_failed reason={} error={}",
                    reason,
                    e.getClass().getSimpleName(),
                    e);
        }
    }

    /** 관리툴 토큰 확인(위조·만료면 거부) 뒤 화이트리스트에서 빠졌으면 거부한다. */
    public OperatorId ensureOperator(String adminToken) {
        OperatorId operator = tokenIssuer.verify(adminToken);
        requireWhitelisted(operator);
        return operator;
    }

    private void requireWhitelisted(OperatorId operator) {
        if (!isWhitelisted(operator)) {
            throw new PermissionDeniedException("운영자 화이트리스트 밖");
        }
    }

    private boolean isWhitelisted(OperatorId operator) {
        SocialAccount account = operator.account();
        return policy.operatorWhitelist()
                .contains(account.provider().name(), account.providerAccountId());
    }

    /** 운영자 로그인 실패 사유(감사 기록의 변경 후 값). */
    enum LoginFailure {
        UNKNOWN_PROVIDER,
        AUTH_FAILED,
        PROVIDER_UNAVAILABLE,
        NOT_WHITELISTED
    }
}
