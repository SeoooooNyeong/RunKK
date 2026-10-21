package com.a207.runkk.infra.integration.auth;

import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.SocialAccount;
import com.a207.runkk.member.TokenIssuer;
import com.a207.runkk.member.TokenPair;
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
import java.util.Date;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * member.TokenIssuer를 HS256 JWT로 구현한다(설계 6.1.13, D-19). 접근 토큰 1시간, 갱신 토큰 14일, 가입 티켓 15분. 토큰 종류를
 * token_type 클레임으로 나눠 한 종류의 토큰으로 다른 종류의 검사를 통과하지 못한다. 갱신 토큰은 한 번만 쓰고(회전) 폐기 목록은
 * RefreshTokenRevocations(Redis + 메모리)에 둬 재시작해도 남는다(코드 리뷰 2차 M6). 토큰 값은 로그에 남기지 않는다.
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    static final Duration ACCESS_TTL = Duration.ofHours(1);
    static final Duration REFRESH_TTL = Duration.ofDays(14);
    static final Duration TICKET_TTL = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(JwtTokenIssuer.class);
    private static final String ISSUER = "runkk";
    private static final String TYPE = "token_type";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";
    private static final String SIGN_UP = "sign_up";
    private static final String PROVIDER = "provider";
    private static final String ACCOUNT = "account";
    private static final int MIN_SECRET_BYTES = 32;

    private final MACSigner signer;
    private final MACVerifier verifier;
    private final Clock clock;
    private final RefreshTokenRevocations revocations;

    @Autowired
    public JwtTokenIssuer(
            @Value("${runkk.auth.jwt-secret:}") String secret,
            Clock clock,
            RefreshTokenRevocations revocations) {
        byte[] key = secretBytes(secret);
        try {
            this.signer = new MACSigner(key);
            this.verifier = new MACVerifier(key);
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 서명 키를 만들 수 없다", e);
        }
        this.clock = clock;
        this.revocations = revocations;
    }

    /** 폐기 목록을 메모리에만 두는 발급기(단위 시험). */
    JwtTokenIssuer(String secret, Clock clock) {
        this(secret, clock, new RefreshTokenRevocations(null, clock));
    }

    @Override
    public TokenPair issue(long memberId) {
        String subject = String.valueOf(memberId);
        return new TokenPair(
                sign(claims(subject, ACCESS, ACCESS_TTL).build()),
                sign(claims(subject, REFRESH, REFRESH_TTL).build()));
    }

    @Override
    public String issueSignUpTicket(SocialAccount account) {
        return sign(
                claims(account.provider() + ":" + account.providerAccountId(), SIGN_UP, TICKET_TTL)
                        .claim(PROVIDER, account.provider().name())
                        .claim(ACCOUNT, account.providerAccountId())
                        .build());
    }

    @Override
    public SocialAccount redeemSignUpTicket(String ticket) {
        JWTClaimsSet claims = parse(ticket, SIGN_UP);
        try {
            return new SocialAccount(
                    ProviderType.valueOf(claims.getStringClaim(PROVIDER)),
                    claims.getStringClaim(ACCOUNT));
        } catch (ParseException | IllegalArgumentException | NullPointerException e) {
            throw new AuthFailureException("가입 티켓 형식 오류", e);
        }
    }

    @Override
    public long verify(String accessToken) {
        return memberIdOf(parse(accessToken, ACCESS));
    }

    @Override
    public void revoke(String refreshToken) {
        try {
            JWTClaimsSet claims = parse(refreshToken, REFRESH);
            revocations.revoke(claims.getJWTID(), claims.getExpirationTime().toInstant());
        } catch (AuthFailureException e) {
            log.debug("revoke_ignored reason=unreadable_token");
        }
    }

    @Override
    public long redeemRefreshToken(String refreshToken) {
        JWTClaimsSet claims = parse(refreshToken, REFRESH);
        Instant expiry = claims.getExpirationTime().toInstant();
        if (!revocations.claim(claims.getJWTID(), expiry)) {
            throw new AuthFailureException("폐기된 갱신 토큰");
        }
        return memberIdOf(claims);
    }

    private JWTClaimsSet.Builder claims(String subject, String type, Duration ttl) {
        Instant now = clock.instant();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(subject)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .claim(TYPE, type);
    }

    private String sign(JWTClaimsSet claims) {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 서명 실패", e);
        }
        return jwt.serialize();
    }

    private JWTClaimsSet parse(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            throw new AuthFailureException("토큰 없음");
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(verifier)) {
                throw new AuthFailureException("서명 불일치");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(claims.getIssuer())
                    || !expectedType.equals(claims.getStringClaim(TYPE))
                    || claims.getExpirationTime() == null
                    || !claims.getExpirationTime().toInstant().isAfter(clock.instant())) {
                throw new AuthFailureException("만료 또는 종류 불일치");
            }
            return claims;
        } catch (ParseException | JOSEException e) {
            throw new AuthFailureException("토큰 형식 오류", e);
        }
    }

    private static long memberIdOf(JWTClaimsSet claims) {
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new AuthFailureException("회원 ID 없음", e);
        }
    }

    private static byte[] secretBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("jwt_secret_missing using=random_key_until_restart");
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            return random;
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET은 32바이트 이상이어야 한다");
        }
        return bytes;
    }
}
