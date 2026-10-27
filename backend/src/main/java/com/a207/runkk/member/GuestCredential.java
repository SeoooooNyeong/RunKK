package com.a207.runkk.member;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 게스트 기기 자격 증명의 형식과 해시 규칙(설계 6.1.2 GuestCredential, D-89, D-164 (2)). 토큰은 {@code
 * g1.<memberId>.<base64url 32바이트>}(패딩 없음, 43자)이고 서버는 비밀의 SHA-256 16진 64자만
 * Member.guestCredentialHash에 둔다. 토큰 원문은 게스트 생성 응답에 한 번만 내주고 저장·로그에 남기지 않는다.
 */
public final class GuestCredential {

    private static final String VERSION = "g1";
    private static final int SECRET_BYTES = 32;
    private static final Pattern TOKEN =
            Pattern.compile("^g1\\.([1-9][0-9]{0,18})\\.([A-Za-z0-9_-]{43})$");
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private GuestCredential() {}

    /** 회원 ID와 무작위 비밀 32바이트로 토큰을 만들고 비밀의 해시를 함께 돌려준다. */
    public static Issued issue(long memberId, SecureRandom random) {
        if (memberId < 1) {
            throw new IllegalArgumentException("회원 ID는 1 이상: " + memberId);
        }
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        String secret = ENCODER.encodeToString(bytes);
        return new Issued(VERSION + "." + memberId + "." + secret, hashOf(secret));
    }

    /** 형식이 틀리면(버전, 양수 회원 ID, 패딩 없는 base64url 43자) 비어 있음. */
    public static Optional<Parsed> parse(String token) {
        if (token == null) {
            return Optional.empty();
        }
        var matcher = TOKEN.matcher(token);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Parsed(Long.parseLong(matcher.group(1)), matcher.group(2)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** SHA-256 16진 64자(소문자). */
    public static String hashOf(String secret) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256이 없다", e);
        }
    }

    /**
     * 발급 결과. toString은 토큰을 감춘다(로그 유출 방지).
     *
     * @param token 기기 자격 증명 원문(한 번만 내준다)
     * @param hash 비밀의 SHA-256 16진 64자
     */
    public record Issued(String token, String hash) {
        @Override
        public String toString() {
            return "Issued[token=***]";
        }
    }

    /**
     * 해독 결과. toString은 비밀을 감춘다.
     *
     * @param memberId 회원 ID
     * @param secret 비밀(base64url 43자)
     */
    public record Parsed(long memberId, String secret) {
        @Override
        public String toString() {
            return "Parsed[memberId=" + memberId + ", secret=***]";
        }
    }
}
