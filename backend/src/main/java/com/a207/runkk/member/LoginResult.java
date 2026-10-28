package com.a207.runkk.member;

import com.a207.runkk.common.ReasonCode;

/**
 * 로그인 결과(SQ-17): 로그인완료 + 토큰 / 가입필요 + 가입 티켓 / 로그인실패 + 사유. 셋 중 하나만 값이 있다.
 *
 * @param tokens 로그인완료면 토큰 쌍
 * @param signUpTicket 가입필요면 15분짜리 가입 티켓
 * @param failure 로그인실패면 사유(PROVIDER_UNAVAILABLE 또는 AUTH_FAILED)
 */
public record LoginResult(TokenPair tokens, String signUpTicket, ReasonCode failure) {

    public static LoginResult loggedIn(TokenPair tokens) {
        return new LoginResult(tokens, null, null);
    }

    public static LoginResult signUpRequired(String ticket) {
        return new LoginResult(null, ticket, null);
    }

    public static LoginResult failed(ReasonCode reason) {
        return new LoginResult(null, null, reason);
    }

    public boolean isLoggedIn() {
        return tokens != null;
    }

    public boolean isSignUpRequired() {
        return signUpTicket != null;
    }

    public boolean isFailed() {
        return failure != null;
    }

    @Override
    public String toString() {
        return "LoginResult[loggedIn="
                + isLoggedIn()
                + ", signUpRequired="
                + isSignUpRequired()
                + ", failure="
                + failure
                + "]";
    }
}
