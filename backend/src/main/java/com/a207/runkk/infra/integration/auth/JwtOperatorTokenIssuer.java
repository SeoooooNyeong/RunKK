package com.a207.runkk.infra.integration.auth;

import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.SocialAccount;
import com.a207.runkk.operator.OperatorId;
import com.a207.runkk.operator.OperatorTokenIssuer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * operator.OperatorTokenIssuer를 HS256 JWT로 구현한다(설계 6.1.13, D-42). 회원 토큰(JwtTokenIssuer)과 서명
 * 키(ADMIN_JWT_SECRET)와 발급자(runkk-admin)·종류 클레임을 나눠 회원 토큰으로 관리툴을 통과하지 못하고 관리툴 토큰으로 회원 API를 통과하지 못한다.
 * 두 키가 같게 설정되면 기동을 멈춘다. 만료 8시간(제안). 토큰 값은 로그에 남기지 않는다.
 */
@Component
public class JwtOperatorTokenIssuer implements OperatorTokenIssuer {

    static final Duration TTL = Duration.ofHours(8);

    private static final Logger log = LoggerFactory.getLogger(JwtOperatorTokenIssuer.class);
    private static final String ISSUER = "runkk-admin";
    private static final String TYPE = "token_type";
    private static final String ADMIN = "admin";
    private static final String PROVIDER = "provider";
    private static final String ACCOUNT = "account";
    private static final int MIN_SECRET_BYTES = 32;

    private final MACSigner signer;
    private final MACVerifier verifier;
    private final Clock clock;

    public JwtOperatorTokenIssuer(
            @Value("${runkk.auth.admin-jwt-secret:}") String secret,
            @Value("${runkk.auth.jwt-secret:}") String memberSecret,
            Clock clock) {
        byte[] key = secretBytes(secret, memberSecret);
        try {
            this.signer = new MACSigner(key);
            this.verifier = new MACVerifier(key);
        } catch (JOSEException e) {
            throw new IllegalStateException("관리툴 JWT 서명 키를 만들 수 없다", e);
        }
        this.clock = clock;
    }

    @Override
    public String issue(OperatorId operator) {
        Instant now = clock.instant();
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject(operator.actorId())
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plus(TTL)))
                        .claim(TYPE, ADMIN)
                        .claim(PROVIDER, operator.account().provider().name())
                        .claim(ACCOUNT, operator.account().providerAccountId())
                        .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("관리툴 JWT 서명 실패", e);
        }
        return jwt.serialize();
    }

    @Override
    public OperatorId verify(String adminToken) {
        if (adminToken == null || adminToken.isBlank()) {
            throw new PermissionDeniedException("관리툴 토큰 없음");
        }
        try {
            JWTClaimsSet claims = verifiedClaims(SignedJWT.parse(adminToken));
            String provider = claims.getStringClaim(PROVIDER);
            if (provider == null) {
                throw new PermissionDeniedException("관리툴 토큰 제공자 없음");
            }
            return new OperatorId(
                    new SocialAccount(
                            ProviderType.valueOf(provider), claims.getStringClaim(ACCOUNT)));
        } catch (ParseException | JOSEException | IllegalArgumentException e) {
            throw new PermissionDeniedException("관리툴 토큰 형식 오류");
        }
    }

    /** 서명(HS256)과 발급자·종류·만료를 확인한 클레임. 어긋나면 PermissionDeniedException(sw-test 복잡도 분리). */
    private JWTClaimsSet verifiedClaims(SignedJWT jwt) throws JOSEException, ParseException {
        if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
            throw new PermissionDeniedException("관리툴 토큰 서명 불일치");
        }
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        if (!ISSUER.equals(claims.getIssuer())
                || !ADMIN.equals(claims.getStringClaim(TYPE))
                || !isUnexpired(claims)) {
            throw new PermissionDeniedException("관리툴 토큰 만료 또는 종류 불일치");
        }
        return claims;
    }

    private boolean isUnexpired(JWTClaimsSet claims) {
        return claims.getExpirationTime() != null
                && claims.getExpirationTime().toInstant().isAfter(clock.instant());
    }

    private static byte[] secretBytes(String secret, String memberSecret) {
        if (secret == null || secret.isBlank()) {
            log.warn("admin_jwt_secret_missing using=random_key_until_restart");
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            return random;
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("ADMIN_JWT_SECRET은 32바이트 이상이어야 한다");
        }
        if (memberSecret != null
                && Arrays.equals(bytes, memberSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("ADMIN_JWT_SECRET은 JWT_SECRET과 달라야 한다(D-42)");
        }
        return bytes;
    }
}
