package com.a207.runkk.member;

/**
 * 회원 접근 토큰과 갱신 토큰(D-19).
 *
 * @param accessToken 접근 토큰(1시간)
 * @param refreshToken 갱신 토큰(14일)
 */
public record TokenPair(String accessToken, String refreshToken) {

    @Override
    public String toString() {
        return "TokenPair[***]";
    }
}
